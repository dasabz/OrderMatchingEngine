public class Trade {
    private long tradeID;
    private int tradeQty;
    private double tradePrice;
    private long transactTime;
    private Order order;

    Trade(long tradeID, int tradeQty, double tradePrice, Order order) {
        this.tradeID = tradeID;
        this.tradeQty = tradeQty;
        this.tradePrice = tradePrice;
        this.order = order;
        this.transactTime = System.nanoTime();
    }

    public long getTradeID() {
        return tradeID;
    }

    public void setTradeID(long tradeID) {
        this.tradeID = tradeID;
    }

    public int getTradeQty() {
        return tradeQty;
    }

    public void setTradeQty(int tradeQty) {
        this.tradeQty = tradeQty;
    }

    @Override
    public String toString() {
        return "Trade [tradeID=" + tradeID + ", tradeQty=" + tradeQty + ", tradePrice=" + tradePrice + ", transactTime=" + transactTime
                + ", order=" + order + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((order == null) ? 0 : order.hashCode());
        long temp;
        temp = Double.doubleToLongBits(tradePrice);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        result = prime * result + tradeQty;
        result = prime * result + (int) (tradeID ^ (tradeID >>> 32));
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Trade other = (Trade) obj;
        if (order == null) {
            if (other.order != null)
                return false;
        } else if (!order.equals(other.order))
            return false;
        return Double.doubleToLongBits(tradePrice) == Double.doubleToLongBits(other.tradePrice) && tradeQty == other.tradeQty && tradeID == other.tradeID;
    }

    public double getTradePrice() {
        return tradePrice;
    }

    public void setTradePrice(double tradePrice) {
        this.tradePrice = tradePrice;
    }

    public long getTransactTime() {
        return transactTime;
    }

    public void setTransactTime(long transactTime) {
        this.transactTime = transactTime;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }
}