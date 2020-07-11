import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OrderMatchingEngineImpl implements OrderMatchingEngine {
    private Map<String, NewOrder> clOrdMap = new HashMap<>();
    private Logger logger = Logger.getLogger(this.getClass().getCanonicalName());
    private Map<String, OrderBook> symbolOrderBook = new HashMap<>(100);
    private List<Order> currentBidOrders = new LinkedList<>();
    private List<Order> currentAskOrders = new LinkedList<>();

    OrderMatchingEngineImpl(String symbol) {
        if (!symbolOrderBook.containsKey(symbol)) {
            symbolOrderBook.put(symbol, new OrderBook());
        }
    }

    @Override
    /*
        This is our current orderbook where we have our client orders
        If we get a market snapshot where the market has moved, we atleast need to try to see if there is any possibility to match our existing orders
        This matching engine is running in a backtesting environment to test algo behaviour , so anyways my understanding is that it will get market data and it just
        needs to match the existing orders it has at whichever levels. So the assumption is market data which it receives does not include the orders it receives from the algo
    */
    public void onMdSnapshot(MarketDataBookSnapshot marketDataBookSnapshot) {
        OrderBook currentOrderBook = symbolOrderBook.get(marketDataBookSnapshot.getSymbol());
        currentBidOrders.clear();
        currentAskOrders.clear();
        //Save the current bid/ask orders which were there in our order book before we got this market data snapshot
        TreeMap<Double, LinkedList<Order>> bidLevels = currentOrderBook.getSameSideLevels(Side.BUY);
        TreeMap<Double, LinkedList<Order>> askLevels = currentOrderBook.getSameSideLevels(Side.SELL);

        for (Map.Entry<Double, LinkedList<Order>> bidLevel : bidLevels.entrySet()) {
            bidLevel.getValue().stream().filter(bidOrder -> !bidOrder.getClOrderId().equals("")).forEach(currentBidOrders::add);
        }
        for (Map.Entry<Double, LinkedList<Order>> askLevel : askLevels.entrySet()) {
            askLevel.getValue().stream().filter(askOrder -> !askOrder.getClOrderId().equals("")).forEach(currentAskOrders::add);
        }
        OrderBook latestOrderBook = new OrderBook();
        latestOrderBook.onMarketOrderBookSnapshot(marketDataBookSnapshot);
        symbolOrderBook.put(marketDataBookSnapshot.getSymbol(), latestOrderBook);
        //Re-enter our orders into the orderbook after the market data snapshot and try to match again
        currentBidOrders.forEach(order -> symbolOrderBook.get(marketDataBookSnapshot.getSymbol()).enterOrder(order));
        currentAskOrders.forEach(order -> symbolOrderBook.get(marketDataBookSnapshot.getSymbol()).enterOrder(order));
    }

    @Override
    public OrderStatus enter(NewOrder order) {
        log(order, "Entering ");
        if (order.getQty() == 0 || order.getPrice() == 0.0)
            return OrderStatus.REJECTED;
        clOrdMap.put(order.getClOrderId(), order);
        if (!symbolOrderBook.containsKey(order.getSymbol())) {
            symbolOrderBook.put(order.getSymbol(), new OrderBook());
        }
        order.setOrderStatus(OrderStatus.NEW);
        OrderBook orderBook = symbolOrderBook.get(order.getSymbol());
        orderBook.enterOrder(order);
        return OrderStatus.NEW;
    }

    @Override
    public OrderStatus enter(AmendOrder order) {
        log(order, "Amending ");
        OrderBook orderBook = symbolOrderBook.get(order.getSymbol());

        if (!clOrdMap.containsKey(order.getOrigClOrderId()) || clOrdMap.get(order.getOrigClOrderId()).getOrderStatus() == OrderStatus.FILLED || (order.getQty() <= 0 && order.getPrice() <= 0.0)) {
            return OrderStatus.REJECTED;
        }
        TreeMap<Double, LinkedList<Order>> sameSideLevels = orderBook.getSameSideLevels(order.getSide());
        TreeMap<Double, LinkedList<Order>> oppositeSideLevels = orderBook.getOppositeSideLevels(order.getSide());
        Order origOrder = clOrdMap.get(order.getOrigClOrderId());

        //If no price change
        if (Double.compare(origOrder.getPrice(), order.getPrice()) == 0) {
            //If quantity amend down only we need to adjust original order
            if (order.getQty() < origOrder.getQty() && (sameSideLevels.containsKey(order.getPrice()))) {
                return amendDownYoungestSliceAndCancelOutstandingSlicesAtLevel(order, sameSideLevels);
            } else if (order.getQty() > origOrder.getQty()) {
                addNewSliceAtSamePriceLevelWithRemainingQty(order, sameSideLevels);
            } else {//If both quantity and price are equal then there is nothing to amend, so we return
                return OrderStatus.REJECTED;
            }
        } else {
            //Both price and quantity amend will lead to loss of queue priority
            // NewOrder looses queuePriority on price amend
            // 1. Cancel the original order slices
            // 2. Add the new order at appropriate level
            removeExistingSliceAndReEnterOrderBookOnLoosingQueuePriority(order, sameSideLevels);
            return OrderStatus.AMENDED;
        }
        orderBook.updateTopOfBook(order, oppositeSideLevels, sameSideLevels);
        return OrderStatus.AMENDED;
    }

    private void removeExistingSliceAndReEnterOrderBookOnLoosingQueuePriority(AmendOrder order, TreeMap<Double, LinkedList<Order>> sameSideLevels) {
        double origOrderPrice = 0;
        for (Map.Entry<Double, LinkedList<Order>> entry : sameSideLevels.entrySet()) {
            for (Order sameLevelOrder : entry.getValue()) {
                if (sameLevelOrder.getClOrderId().equals(order.getOrigClOrderId())) {
                    origOrderPrice = sameLevelOrder.getPrice();
                }
            }
        }
        if (sameSideLevels.containsKey(origOrderPrice)) {
            sameSideLevels.get(origOrderPrice).removeIf(sameSideOrder -> sameSideOrder.getClOrderId().equals(order.getOrigClOrderId()));
        }
        enter(new NewOrder(order.getSymbol(), order.getSide(), order.getQty(), order.getPrice(), order.getOrigClOrderId()));
    }

    private void addNewSliceAtSamePriceLevelWithRemainingQty(AmendOrder order, TreeMap<Double, LinkedList<Order>> sameSideLevels) {
        if (sameSideLevels.containsKey(order.getPrice())) {
            //Find the original order in the level and get its quantity
            Optional<Integer> origOrderQty = sameSideLevels.get(order.getPrice()).stream().filter(sameSideOrder -> sameSideOrder.getClOrderId().equals(order.getOrigClOrderId())).map(Order::getQty).findFirst();
            int newSliceOrderQty = order.getQty();
            if (origOrderQty.isPresent()) {
                newSliceOrderQty = order.getQty() - origOrderQty.get();
            }
            //We need to add a new slice at the end of the list of that price level with the clientOrderId as the original order
            sameSideLevels.get(order.getPrice()).addLast(new NewOrder(order.getSymbol(), order.getSide(), newSliceOrderQty, order.getPrice(), order.getOrigClOrderId()));
        }
    }

    private OrderStatus amendDownYoungestSliceAndCancelOutstandingSlicesAtLevel(AmendOrder order, TreeMap<Double, LinkedList<Order>> sameSideLevels) {
        LinkedList<Order> sameSideOrders = sameSideLevels.get(order.getPrice());
        Iterator<Order> sameSideOrdersReverseIterator = sameSideOrders.descendingIterator();
        int orderCount = 0;
        while (sameSideOrdersReverseIterator.hasNext()) {
            Order sameSideOrder = sameSideOrdersReverseIterator.next();
            //Amend down the youngest slice first
            if (sameSideOrder.getClOrderId().equals(order.getOrigClOrderId())) {
                orderCount++;
                if (orderCount == 1) {
                    sameSideOrder.setQty(order.getQty());
                    sameSideOrder.setOrderStatus(OrderStatus.AMENDED);
                    order.setOrderStatus(OrderStatus.AMENDED);
                } else {
                    //Subsequent slices for this order need to be removed from this price level
                    sameSideOrdersReverseIterator.remove();
                }
            }
        }
        return OrderStatus.AMENDED;
    }


    @Override
    public OrderStatus enter(CancelOrder order) {
        log(order, "Cancelling ");
        OrderBook orderBook = symbolOrderBook.get(order.getSymbol());
        if (clOrdMap.containsKey(order.getOrigClOrderId())) {
            clOrdMap.remove(clOrdMap.get(order.getOrigClOrderId()).getClOrderId());
        } else {
            return OrderStatus.REJECTED;
        }
        removeOriginalOrderAndClearLevelIfNeeded(orderBook.getSameSideLevels(order.getSide()), order.getOrigClOrderId());
        orderBook.updateTopOfBook(order, orderBook.getOppositeSideLevels(order.getSide()), orderBook.getSameSideLevels(order.getSide()));
        return OrderStatus.CANCELLED;
    }

    private void removeOriginalOrderAndClearLevelIfNeeded(TreeMap<Double, LinkedList<Order>> sameSideLevels, String origClOrderId) {
        Iterator<Map.Entry<Double, LinkedList<Order>>> sameSidePriceLevelIterator = sameSideLevels.entrySet().iterator();
        while (sameSidePriceLevelIterator.hasNext()) {
            Map.Entry<Double, LinkedList<Order>> priceLevel = sameSidePriceLevelIterator.next();
            priceLevel.getValue().removeIf(levelOrder -> levelOrder.getClOrderId().equals(origClOrderId));
            if (priceLevel.getValue().isEmpty()) {
                sameSidePriceLevelIterator.remove();
            }
        }
    }

    private void log(CancelOrder order, String s) {
        logger.log(Level.INFO, s + order.getSide() + " order for quantity=" + order.getQty() + " price=" + order.getPrice()
                + " newClOrdId=" + order.getClOrderId());
    }

    private void log(AmendOrder order, String s) {
        logger.log(Level.INFO, s + order.getSide() + " order for quantity=" + order.getQty() + " price=" + order.getPrice()
                + " oldClOrdId=" + order.getOrigClOrderId() + " newClOrdId=" + order.getClOrderId());
    }

    private void log(NewOrder order, String msg) {
        logger.log(Level.INFO, msg + order.getSide() + " order for quantity=" + order.getQty() + " price=" + order.getPrice() + " clientOrderId=" + order.getClOrderId());
    }

    @Override
    public TopOfBook getTopOfBook(String symbol) {
        return symbolOrderBook.get(symbol).getTopOfBook();
    }

    @Override
    public List<Trade> getTrades(Order order) {
        return symbolOrderBook.get(order.getSymbol()).getTrade(order.getClOrderId());
    }

    public List<Trade> getTrades(String symbol, String clOrdId) {
        return symbolOrderBook.get(symbol).getTrade(clOrdId);
    }

    @Override
    public String dumpOrderBook(String symbol) {
        return symbolOrderBook.get(symbol).dumpOrderBook();
    }
}