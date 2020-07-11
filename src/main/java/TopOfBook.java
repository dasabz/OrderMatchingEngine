import java.util.Objects;

public class TopOfBook {
    private final String symbol;
    private final double bestBidPrice;
    private final double bestAskPrice;
    private final int bestBidQuantity;
    private final int bestAskQuantity;

    TopOfBook(String symbol, double bestBidPrice, double bestAskPrice, int bestBidQuantity, int bestAskQuantity) {
        this.symbol = symbol;
        this.bestBidPrice = bestBidPrice;
        this.bestAskPrice = bestAskPrice;
        this.bestBidQuantity = bestBidQuantity;
        this.bestAskQuantity = bestAskQuantity;
    }

    @Override
    public String toString() {
        return "TopOfBook{" +
                "bestBidPrice=" + bestBidPrice +
                ", bestAskPrice=" + bestAskPrice +
                ", bestBidQuantity=" + bestBidQuantity +
                ", bestAskQuantity=" + bestAskQuantity +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TopOfBook topOfBook = (TopOfBook) o;
        return Double.compare(topOfBook.bestBidPrice, bestBidPrice) == 0 &&
                Double.compare(topOfBook.bestAskPrice, bestAskPrice) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(bestBidPrice, bestAskPrice);
    }
}
