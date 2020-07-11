import java.util.List;

public interface OrderMatchingEngine {
    void onMdSnapshot(MarketDataBookSnapshot marketDataBookSnapshot);

    OrderStatus enter(NewOrder order);

    OrderStatus enter(AmendOrder order);

    OrderStatus enter(CancelOrder order);

    TopOfBook getTopOfBook(String symbol);

    List<Trade> getTrades(Order order);

    String dumpOrderBook(String symbol);
}
