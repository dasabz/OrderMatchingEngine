public interface Order {
    boolean isMoreAggressive(Order oppositeOrder);

    boolean isFullyFilled();

    void reduceQty(int qty);

    OrderStatus getOrderStatus();

    void setOrderStatus(OrderStatus orderStatus);

    double getPrice();

    Side getSide();

    String getSymbol();

    int getQty();

    void setQty(int quantity);

    String getClOrderId();
}
