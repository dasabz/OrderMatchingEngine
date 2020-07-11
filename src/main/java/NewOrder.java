import java.util.Objects;

public class NewOrder implements Order {
    private final String symbol;
    private final Side side;
    private String clOrderId;
    private OrderStatus orderStatus;
    private int qty;
    private double price;

    NewOrder(String symbol, Side side, int quantity, double price, String clientOrderId) {
        this.clOrderId = clientOrderId;
        this.symbol = symbol;
        this.side = side;
        this.qty = quantity;
        this.price = price;
        this.orderStatus = OrderStatus.NONE;
    }

    @Override
    public boolean isMoreAggressive(Order oppositeOrder) {
        return (this.getSide() == Side.BUY && this.getPrice() >= oppositeOrder.getPrice()
                || this.getSide() == Side.SELL && this.getPrice() <= oppositeOrder.getPrice());
    }

    @Override
    public boolean isFullyFilled() {
        return this.getQty() <= 0;
    }

    @Override
    public void reduceQty(int qty) {
        this.qty = this.qty - qty;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NewOrder order = (NewOrder) o;
        return qty == order.qty &&
                Double.compare(order.price, price) == 0 &&
                Objects.equals(symbol, order.symbol) &&
                side == order.side &&
                Objects.equals(clOrderId, order.clOrderId) &&
                orderStatus == order.orderStatus;
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbol, side, qty, price, clOrderId, orderStatus);
    }

    public int getQty() {
        return qty;
    }

    public void setQty(int qty) {
        this.qty = qty;
    }

    @Override
    public String toString() {
        return "Order [ symbol=" + symbol
                + ", qty="
                + qty + ", price=" + price + ", orderStatus=" + orderStatus + ", side=" + side
                + ", clOrderId=" + clOrderId + "]";
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public Side getSide() {
        return side;
    }

    public String getClOrderId() {
        return clOrderId;
    }

    public void setClOrderId(String clOrderId) {
        this.clOrderId = clOrderId;
    }

    public String getSymbol() {
        return symbol;
    }

    public OrderStatus getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(OrderStatus orderStatus) {
        this.orderStatus = orderStatus;
    }


}