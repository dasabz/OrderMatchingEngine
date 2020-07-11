public class MarketDataBookSnapshot {
    private final String symbol;
    private final double prices[];
    private final int quantities[];

    MarketDataBookSnapshot(String symbol, double prices[], int quantities[]) {
        this.symbol = symbol;
        this.prices = prices;
        this.quantities = quantities;
    }

    public String getSymbol() {
        return symbol;
    }

    public double[] getPrices() {
        return prices;
    }

    public int[] getQuantities() {
        return quantities;
    }

}
