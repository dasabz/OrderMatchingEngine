import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class OrderBook {
    private static long tradeId = 10000;
    private TreeMap<Double, LinkedList<Order>> bidLevels = new TreeMap<>(Comparator.reverseOrder());
    private TreeMap<Double, LinkedList<Order>> askLevels = new TreeMap<>(Comparator.naturalOrder());
    private TopOfBook topOfBook;
    private double bestBidPrice;
    private double bestAskPrice;
    private int bestBidQuantity;
    private int bestAskQuantity;
    private Logger logger = Logger.getLogger(this.getClass().getName());
    private OrderValidator orderValidator = new OrderValidatorImpl();
    private LogPrinting logPrinting = new LogPrinting();
    private Map<String, List<Trade>> tradesPerOrder = new HashMap<>();

    public List<Trade> getTrade(String clOrdId) {
        return tradesPerOrder.getOrDefault(clOrdId, null);
    }

    public TopOfBook getTopOfBook() {
        return topOfBook;
    }

    public void onMarketOrderBookSnapshot(MarketDataBookSnapshot marketDataBookSnapshot) {
        bidLevels.clear();
        askLevels.clear();
        populateBidBookInReverseOrder(marketDataBookSnapshot);
        populateAskBookInNaturalOrder(marketDataBookSnapshot);
        topOfBook = new TopOfBook(marketDataBookSnapshot.getSymbol(), bestBidPrice, bestAskPrice, bestBidQuantity, bestAskQuantity);
    }

    private void populateBidBookInReverseOrder(MarketDataBookSnapshot marketDataBookSnapshot) {
        int quantities[] = marketDataBookSnapshot.getQuantities();
        double prices[] = marketDataBookSnapshot.getPrices();
        //MarketDataSnapshot only has level 2 data so each priceLevel will only have 1 quantity
        for (int i = 4; i >= 0; i--) {
            LinkedList<Order> bidOrdersAtLevel = new LinkedList<>();
            bidOrdersAtLevel.add(new NewOrder(marketDataBookSnapshot.getSymbol(), Side.BUY, quantities[i], prices[i], ""));
            bidLevels.put(prices[i], bidOrdersAtLevel);
        }
        bestBidPrice = prices[4];
        bestBidQuantity = quantities[4];
    }

    private void populateAskBookInNaturalOrder(MarketDataBookSnapshot marketDataBookSnapshot) {
        int quantities[] = marketDataBookSnapshot.getQuantities();
        double prices[] = marketDataBookSnapshot.getPrices();
        //MarketDataSnapshot only has level 2 data so each priceLevel will only have 1 quantity
        for (int i = 5; i <= 9; i++) {
            LinkedList<Order> askOrdersAtLevel = new LinkedList<>();
            askOrdersAtLevel.add(new NewOrder(marketDataBookSnapshot.getSymbol(), Side.SELL, quantities[i], prices[i], ""));
            askLevels.put(prices[i], askOrdersAtLevel);
        }
        bestAskPrice = prices[5];
        bestAskQuantity = quantities[5];
    }

    public TreeMap<Double, LinkedList<Order>> getOppositeSideLevels(Side side) {
        return (side == Side.BUY) ? this.askLevels : this.bidLevels;
    }

    public TreeMap<Double, LinkedList<Order>> getSameSideLevels(Side side) {
        return (side == Side.BUY) ? this.bidLevels : this.askLevels;
    }

    public void enterOrder(Order order) {
        if (!orderValidator.isValid(order)) {
            return;
        }
        TreeMap<Double, LinkedList<Order>> oppositeSideLevels = getOppositeSideLevels(order.getSide());
        TreeMap<Double, LinkedList<Order>> sameSideLevels = getSameSideLevels(order.getSide());

        if (isLessAggressive(order.getSide(), order.getPrice(), oppositeSideLevels.firstKey())) {
            //If less aggressive then opposite side top of the book, it is a resting order, so no need to try to match
            addToOrderBook(order, sameSideLevels);
        } else {
            //If more aggressive than opposite side top of the book, then we try to match and add residual quantity to order book
            boolean hasLeavesQty = matchOrder(order, oppositeSideLevels);
            if (hasLeavesQty) {
                //Add leftover quantity after match to order book
                addToOrderBook(order, sameSideLevels);
            }
        }
        //Update top of book accordingly
        updateTopOfBook(order, oppositeSideLevels, sameSideLevels);
    }

    private boolean isLessAggressive(Side side, Double orderPrice, Double oppositeOrderPrice) {
        return (side == Side.BUY && orderPrice < oppositeOrderPrice
                || side == Side.SELL && orderPrice > oppositeOrderPrice);
    }

    private boolean matchOrder(Order order, TreeMap<Double, LinkedList<Order>> oppositeOrderLevels) {
        Iterator<Map.Entry<Double, LinkedList<Order>>> oppositeOrderLevelIter = oppositeOrderLevels.entrySet().iterator();
        while (oppositeOrderLevelIter.hasNext()) {
            Map.Entry<Double, LinkedList<Order>> oppositeSidePriceLevel = oppositeOrderLevelIter.next();
            List<Order> oppositeOrdersAtLevel = oppositeSidePriceLevel.getValue();
            Iterator<Order> oppositeOrderIterator = oppositeOrdersAtLevel.iterator();
            while (oppositeOrderIterator.hasNext()) {
                Order oppositeOrder = oppositeOrderIterator.next();
                if (oppositeOrder.isMoreAggressive(order)) {
                    int tradeQty;
                    if (order.getQty() > oppositeOrder.getQty()) {
                        oppositeOrderIterator.remove();
                        removePriceLevelIfEmpty(oppositeOrderLevelIter, oppositeSidePriceLevel);
                        tradeQty = oppositeOrder.getQty();
                        updateBothSideOrders(order, OrderStatus.PARTIALLY_FILLED, oppositeOrder.getQty(), oppositeOrder, OrderStatus.FILLED, oppositeOrder.getQty());
                    } else if (order.getQty() < oppositeOrder.getQty()) {
                        tradeQty = order.getQty();
                        updateBothSideOrders(order, OrderStatus.FILLED, order.getQty(), oppositeOrder, OrderStatus.PARTIALLY_FILLED, order.getQty());
                    } else {
                        tradeQty = order.getQty();
                        removePriceLevelIfEmpty(oppositeOrderLevelIter, oppositeSidePriceLevel);
                        updateBothSideOrders(order, OrderStatus.FILLED, order.getQty(), oppositeOrder, OrderStatus.FILLED, order.getQty());
                        oppositeOrderIterator.remove();
                    }
                    generateTrade(order, oppositeOrder, tradeQty);
                    if (order.isFullyFilled()) {
                        removePriceLevelIfEmpty(oppositeOrderLevelIter, oppositeSidePriceLevel);
                        return false;
                    }
                } else {
                    break;
                }
            }
        }
        return true;
    }

    private void removePriceLevelIfEmpty(Iterator<Map.Entry<Double, LinkedList<Order>>> priceLevelIterator, Map.Entry<Double, LinkedList<Order>> priceLevel) {
        if (priceLevel.getValue().isEmpty()) {
            priceLevelIterator.remove();
        }
    }

    private void addToOrderBook(Order order, TreeMap<Double, LinkedList<Order>> sameSideOrderLevels) {
        if (sameSideOrderLevels.containsKey(order.getPrice())) {
            //If an existing price level exists, then add the new order at the end of the last
            sameSideOrderLevels.get(order.getPrice()).addLast(order);
        } else {
            //It means the it is a new price which is not there in the order book at this point, we need to create a new price level
            //If the order is greater than top of book, we make it the new top of book
            LinkedList<Order> sameSideLevel = new LinkedList<>();
            sameSideLevel.add(order);
            sameSideOrderLevels.put(order.getPrice(), sameSideLevel);
        }
        updateTopOfBook(order, getOppositeSideLevels(order.getSide()), sameSideOrderLevels);
    }

    private void updateBothSideOrders(Order order, OrderStatus orderStatus, int orderReduceQty, Order oppositeOrder, OrderStatus oppositeOrderStatus, int oppOrderReduceQty) {
        order.reduceQty(orderReduceQty);
        order.setOrderStatus(orderStatus);
        oppositeOrder.setOrderStatus(oppositeOrderStatus);
        oppositeOrder.reduceQty(oppOrderReduceQty);
    }

    public void updateTopOfBook(Order order, TreeMap<Double, LinkedList<Order>> oppositeOrderLevels, TreeMap<Double, LinkedList<Order>> sameSideOrderLevels) {
        Order sameSideTOB = null;
        Order oppSideTOB = null;
        Optional<Integer> sumOfSameSideTOB = Optional.empty();
        Optional<Integer> sumOfOppSideTOB = Optional.empty();

        if (!sameSideOrderLevels.isEmpty() && !sameSideOrderLevels.get(sameSideOrderLevels.firstKey()).isEmpty()) {
            sameSideTOB = sameSideOrderLevels.get(sameSideOrderLevels.firstKey()).getFirst();
            sumOfSameSideTOB = sameSideOrderLevels.get(sameSideOrderLevels.firstKey()).stream().map(Order::getQty).reduce(Integer::sum);
        }
        if (!oppositeOrderLevels.isEmpty() && !oppositeOrderLevels.get(oppositeOrderLevels.firstKey()).isEmpty()) {
            oppSideTOB = oppositeOrderLevels.get(oppositeOrderLevels.firstKey()).getFirst();
            sumOfOppSideTOB = oppositeOrderLevels.get(oppositeOrderLevels.firstKey()).stream().map(Order::getQty).reduce(Integer::sum);
        }

        double sameSidePrice = -1;
        double oppSidePrice = -1;
        int sameSideQty = -1;
        int oppSideQty = -1;
        if (sameSideTOB != null && sumOfSameSideTOB.isPresent()) {
            sameSidePrice = sameSideTOB.getPrice();
            sameSideQty = sumOfSameSideTOB.get();
        }
        if (oppSideTOB != null && sumOfOppSideTOB.isPresent()) {
            oppSidePrice = oppSideTOB.getPrice();
            oppSideQty = sumOfOppSideTOB.get();

        }
        if (order.getSide() == Side.BUY) {
            topOfBook = new TopOfBook(order.getSymbol(), sameSidePrice, oppSidePrice, sameSideQty, oppSideQty);
        } else {
            topOfBook = new TopOfBook(order.getSymbol(), oppSidePrice, sameSidePrice, oppSideQty, sameSideQty);
        }
    }

    private void generateTrade(Order order, Order oppositeOrder, int tradeQty) {
        Trade trade = new Trade(tradeId++, tradeQty, oppositeOrder.getPrice(), order);
        if (tradesPerOrder.containsKey(order.getClOrderId())) {
            tradesPerOrder.get(order.getClOrderId()).add(trade);
        } else {
            List<Trade> trades = new ArrayList<>();
            trades.add(trade);
            tradesPerOrder.put(order.getClOrderId(), trades);
        }
        logger.log(Level.INFO, trade.toString());
    }

    public String dumpOrderBook() {
        Map<Double, List<Integer>> bids = logPrinting.bids;
        Map<Double, List<Integer>> asks = logPrinting.asks;
        StringBuilder orderBookPrinter = logPrinting.orderBookPrinter;
        clear(bids, asks, orderBookPrinter);
        for (Map.Entry<Double, LinkedList<Order>> bidLevel : bidLevels.entrySet()) {
            bids.put(bidLevel.getKey(), bidLevel.getValue().stream().map(Order::getQty).collect(Collectors.toList()));
        }
        for (Map.Entry<Double, LinkedList<Order>> askLevel : askLevels.entrySet()) {
            asks.put(askLevel.getKey(), askLevel.getValue().stream().map(Order::getQty).collect(Collectors.toList()));
        }
        orderBookPrinter.append("___________________________").append("\n");
        for (Map.Entry<Double, List<Integer>> entry : asks.entrySet()) {
            appendForPrinting(orderBookPrinter, entry, orderBookPrinter.append("              "));
        }
        for (Map.Entry<Double, List<Integer>> entry : bids.entrySet()) {
            appendForPrinting(orderBookPrinter, entry, orderBookPrinter);
        }
        orderBookPrinter.append("___________________________");
        logger.info(orderBookPrinter.toString());
        return orderBookPrinter.toString();
    }

    private void appendForPrinting(StringBuilder orderBookPrinter, Map.Entry<Double, List<Integer>> entry, StringBuilder append) {
        append.append(entry.getKey()).append(" ");
        StringBuilder perLevelPrinter = new StringBuilder();
        if (entry.getValue().size() > 1) {
            entry.getValue().forEach(qty -> perLevelPrinter.append(qty).append(" "));
        } else {
            entry.getValue().forEach(perLevelPrinter::append);
        }
        orderBookPrinter.append(perLevelPrinter.toString()).append("\n");
    }

    private void clear(Map<Double, List<Integer>> bids, Map<Double, List<Integer>> asks, StringBuilder orderBookPrinter) {
        bids.clear();
        asks.clear();
        orderBookPrinter.setLength(0);
    }

    private class LogPrinting {
        Map<Double, List<Integer>> bids = new TreeMap<>(Comparator.reverseOrder());
        Map<Double, List<Integer>> asks = new TreeMap<>(Comparator.reverseOrder());
        StringBuilder orderBookPrinter = new StringBuilder();
    }

}
