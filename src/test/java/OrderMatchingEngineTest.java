import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class OrderMatchingEngineTest {
    private double prices[] = new double[]{97, 98, 99, 100, 101, 103, 104, 105, 106, 107};
    private int quantities[] = new int[]{500, 400, 300, 300, 100, 500, 1000, 2000, 2500, 3000};
    private OrderMatchingEngineImpl orderMatchingEngine = new OrderMatchingEngineImpl("AAPL");
    private String clientName = "CLIENT";
    private void assertInitialOrderBookIsCreatedAsExpectedFromMdSnapshot() {
        orderMatchingEngine.onMdSnapshot(new MarketDataBookSnapshot("AAPL", prices, quantities));
        assertEquals("___________________________\n" +
                        "              107.0 3000\n" +
                        "              106.0 2500\n" +
                        "              105.0 2000\n" +
                        "              104.0 1000\n" +
                        "              103.0 500\n" +
                        "101.0 100\n" +
                        "100.0 300\n" +
                        "99.0 300\n" +
                        "98.0 400\n" +
                        "97.0 500\n" +
                        "___________________________"
                , orderMatchingEngine.dumpOrderBook("AAPL"));
    }

    @Test
    public void singleOrderMultipleTimesAmendUpDownBothQtyAndPriceFollowedByCancel() {
        assertInitialOrderBookIsCreatedAsExpectedFromMdSnapshot();
        assertTopOfBook("AAPL", 101, 103, 100, 500);

        //Send a passive buy of 100@101 and 100 qty gets added at end of list
        NewOrder passiveBuyAtBestBid = new NewOrder("AAPL", Side.BUY, 100, 101, clientName + 1);
        orderMatchingEngine.enter(passiveBuyAtBestBid);
        assertEquals("___________________________\n" +
                        "              107.0 3000\n" +
                        "              106.0 2500\n" +
                        "              105.0 2000\n" +
                        "              104.0 1000\n" +
                        "              103.0 500\n" +
                        "101.0 100 100 \n" +
                        "100.0 300\n" +
                        "99.0 300\n" +
                        "98.0 400\n" +
                        "97.0 500\n" +
                        "___________________________"
                , orderMatchingEngine.dumpOrderBook("AAPL"));

        //Amend up from 100 to 200 at the 101 price level and a new slice will get added, so queue priority will be preserved
        AmendOrder passiveBuyModUp1 = new AmendOrder("AAPL", Side.BUY, 200, 101, clientName + 1, clientName + 2);
        assertEquals(OrderStatus.AMENDED, orderMatchingEngine.enter(passiveBuyModUp1));

        assertEquals("___________________________\n" +
                        "              107.0 3000\n" +
                        "              106.0 2500\n" +
                        "              105.0 2000\n" +
                        "              104.0 1000\n" +
                        "              103.0 500\n" +
                        "101.0 100 100 100 \n" +
                        "100.0 300\n" +
                        "99.0 300\n" +
                        "98.0 400\n" +
                        "97.0 500\n" +
                        "___________________________"
                , orderMatchingEngine.dumpOrderBook("AAPL"));


        //Amend up from 200 to 300 at the 101 price level
        AmendOrder passiveBuyModUp2 = new AmendOrder("AAPL", Side.BUY, 200, 101, clientName + 1, clientName + 3);
        assertEquals(OrderStatus.AMENDED, orderMatchingEngine.enter(passiveBuyModUp2));

        assertEquals("___________________________\n" +
                        "              107.0 3000\n" +
                        "              106.0 2500\n" +
                        "              105.0 2000\n" +
                        "              104.0 1000\n" +
                        "              103.0 500\n" +
                        "101.0 100 100 100 100 \n" +
                        "100.0 300\n" +
                        "99.0 300\n" +
                        "98.0 400\n" +
                        "97.0 500\n" +
                        "___________________________"
                , orderMatchingEngine.dumpOrderBook("AAPL"));


        //Amend down from 200 to 90 and the youngest slice is amended down so queue priority is preserved
        AmendOrder passiveBuyModDown1 = new AmendOrder("AAPL", Side.BUY, 90, 101, clientName + 1, clientName + 4);
        assertEquals(OrderStatus.AMENDED, orderMatchingEngine.enter(passiveBuyModDown1));

        assertEquals("___________________________\n" +
                        "              107.0 3000\n" +
                        "              106.0 2500\n" +
                        "              105.0 2000\n" +
                        "              104.0 1000\n" +
                        "              103.0 500\n" +
                        "101.0 100 90 \n" +
                        "100.0 300\n" +
                        "99.0 300\n" +
                        "98.0 400\n" +
                        "97.0 500\n" +
                        "___________________________"
                , orderMatchingEngine.dumpOrderBook("AAPL"));


        //Amend up from 90 to 110 at the 101 price level
        AmendOrder passiveBuyModUp3 = new AmendOrder("AAPL", Side.BUY, 110, 101, clientName + 1, clientName + 5);
        assertEquals(OrderStatus.AMENDED, orderMatchingEngine.enter(passiveBuyModUp3));

        assertEquals("___________________________\n" +
                        "              107.0 3000\n" +
                        "              106.0 2500\n" +
                        "              105.0 2000\n" +
                        "              104.0 1000\n" +
                        "              103.0 500\n" +
                        "101.0 100 90 20 \n" +
                        "100.0 300\n" +
                        "99.0 300\n" +
                        "98.0 400\n" +
                        "97.0 500\n" +
                        "___________________________"
                , orderMatchingEngine.dumpOrderBook("AAPL"));

        //Cancel the order and ensure the multiple slices are cancelled
        CancelOrder cancelOrder = new CancelOrder("AAPL",Side.BUY,110,101,clientName+1, clientName+6);
        assertEquals(OrderStatus.CANCELLED, orderMatchingEngine.enter(cancelOrder));

        assertInitialOrderBookIsCreatedAsExpectedFromMdSnapshot();
    }

    @Test
    public void singleOrderMultipleTimesAmendUpDownBothQtyAndPrice() {
        assertInitialOrderBookIsCreatedAsExpectedFromMdSnapshot();
        assertTopOfBook("AAPL", 101, 103, 100, 500);

        //Send passive buy 100@101
        NewOrder passiveBuyAtBestBid = new NewOrder("AAPL", Side.BUY, 100, 101, clientName + 8);
        orderMatchingEngine.enter(passiveBuyAtBestBid);
        assertEquals("___________________________\n" +
                        "              107.0 3000\n" +
                        "              106.0 2500\n" +
                        "              105.0 2000\n" +
                        "              104.0 1000\n" +
                        "              103.0 500\n" +
                        "101.0 100 100 \n" +
                        "100.0 300\n" +
                        "99.0 300\n" +
                        "98.0 400\n" +
                        "97.0 500\n" +
                        "___________________________"
                , orderMatchingEngine.dumpOrderBook("AAPL"));

        //Amend down will modify the original order quantity from 100 to 10 at the 101 price level
        AmendOrder passiveBuyQtyModDown = new AmendOrder("AAPL", Side.BUY, 10, 101, clientName + 8, clientName + 9);
        assertEquals(OrderStatus.AMENDED, orderMatchingEngine.enter(passiveBuyQtyModDown));

        assertEquals("___________________________\n" +
                        "              107.0 3000\n" +
                        "              106.0 2500\n" +
                        "              105.0 2000\n" +
                        "              104.0 1000\n" +
                        "              103.0 500\n" +
                        "101.0 100 10 \n" +
                        "100.0 300\n" +
                        "99.0 300\n" +
                        "98.0 400\n" +
                        "97.0 500\n" +
                        "___________________________"
                , orderMatchingEngine.dumpOrderBook("AAPL"));


        //Amend up will add a new slice at the existing level of 101 (So we have 2 slices of 10 now at that level)
        AmendOrder passiveBuyQtyModUp = new AmendOrder("AAPL", Side.BUY, 20, 101, clientName + 8, clientName + 10);
        assertEquals(OrderStatus.AMENDED, orderMatchingEngine.enter(passiveBuyQtyModUp));

        assertEquals("___________________________\n" +
                        "              107.0 3000\n" +
                        "              106.0 2500\n" +
                        "              105.0 2000\n" +
                        "              104.0 1000\n" +
                        "              103.0 500\n" +
                        "101.0 100 10 10 \n" +
                        "100.0 300\n" +
                        "99.0 300\n" +
                        "98.0 400\n" +
                        "97.0 500\n" +
                        "___________________________"
                , orderMatchingEngine.dumpOrderBook("AAPL"));


        //Amend both price and quantity mod up but passive price
        AmendOrder passiveBothPriceAndQtyUp = new AmendOrder("AAPL", Side.BUY, 20, 100, clientName + 8, clientName + 11);
        assertEquals(OrderStatus.AMENDED, orderMatchingEngine.enter(passiveBothPriceAndQtyUp));

        assertEquals("___________________________\n" +
                        "              107.0 3000\n" +
                        "              106.0 2500\n" +
                        "              105.0 2000\n" +
                        "              104.0 1000\n" +
                        "              103.0 500\n" +
                        "101.0 100\n" +
                        "100.0 300 20 \n" +
                        "99.0 300\n" +
                        "98.0 400\n" +
                        "97.0 500\n" +
                        "___________________________"
                , orderMatchingEngine.dumpOrderBook("AAPL"));


        //Amend to make price aggressive and mod quantity up
        AmendOrder passiveBothPriceAndQtyUp1 = new AmendOrder("AAPL", Side.BUY, 200, 101, clientName + 8, clientName + 12);
        assertEquals(OrderStatus.AMENDED, orderMatchingEngine.enter(passiveBothPriceAndQtyUp1));

        assertEquals("___________________________\n" +
                        "              107.0 3000\n" +
                        "              106.0 2500\n" +
                        "              105.0 2000\n" +
                        "              104.0 1000\n" +
                        "              103.0 500\n" +
                        "101.0 100 200 \n" +
                        "100.0 300\n" +
                        "99.0 300\n" +
                        "98.0 400\n" +
                        "97.0 500\n" +
                        "___________________________"
                , orderMatchingEngine.dumpOrderBook("AAPL"));


        //Amend to make price aggressive and mod quantity up to hit the spread
        AmendOrder passiveBothPriceAndQtyUp2 = new AmendOrder("AAPL", Side.BUY, 500, 103, clientName + 8, clientName + 13);
        assertEquals(OrderStatus.AMENDED, orderMatchingEngine.enter(passiveBothPriceAndQtyUp2));

        assertEquals("___________________________\n" +
                        "              107.0 3000\n" +
                        "              106.0 2500\n" +
                        "              105.0 2000\n" +
                        "              104.0 1000\n" +
                        "101.0 100\n" +
                        "100.0 300\n" +
                        "99.0 300\n" +
                        "98.0 400\n" +
                        "97.0 500\n" +
                        "___________________________"
                , orderMatchingEngine.dumpOrderBook("AAPL"));

    }

    @Test
    public void multipleBuySellAggressivePassiveMultipleOrdersAtSameTime() {
        assertInitialOrderBookIsCreatedAsExpectedFromMdSnapshot();
        assertTopOfBook("AAPL", 101, 103, 100, 500);
        //Send passive buy 100@101
        NewOrder passiveBuy1 = new NewOrder("AAPL", Side.BUY, 100, 101, clientName + 1);
        assertEquals(OrderStatus.NEW, orderMatchingEngine.enter(passiveBuy1));
        assertEquals("___________________________\n" +
                        "              107.0 3000\n" +
                        "              106.0 2500\n" +
                        "              105.0 2000\n" +
                        "              104.0 1000\n" +
                        "              103.0 500\n" +
                        "101.0 100 100 \n" +
                        "100.0 300\n" +
                        "99.0 300\n" +
                        "98.0 400\n" +
                        "97.0 500\n" +
                        "___________________________"
                , orderMatchingEngine.dumpOrderBook("AAPL"));
        //Send aggressive buy 50@102
        NewOrder aggressiveBuy1 = new NewOrder("AAPL", Side.BUY, 50, 102, clientName + 2);
        orderMatchingEngine.enter(aggressiveBuy1);
        assertEquals("___________________________\n" +
                        "              107.0 3000\n" +
                        "              106.0 2500\n" +
                        "              105.0 2000\n" +
                        "              104.0 1000\n" +
                        "              103.0 500\n" +
                        "102.0 50\n" +
                        "101.0 100 100 \n" +
                        "100.0 300\n" +
                        "99.0 300\n" +
                        "98.0 400\n" +
                        "97.0 500\n" +
                        "___________________________"
                , orderMatchingEngine.dumpOrderBook("AAPL"));
        //Send passive sell 100@103
        NewOrder passiveSell1 = new NewOrder("AAPL", Side.SELL, 100, 103, clientName + 3);
        orderMatchingEngine.enter(passiveSell1);
        assertEquals("___________________________\n" +
                        "              107.0 3000\n" +
                        "              106.0 2500\n" +
                        "              105.0 2000\n" +
                        "              104.0 1000\n" +
                        "              103.0 500 100 \n" +
                        "102.0 50\n" +
                        "101.0 100 100 \n" +
                        "100.0 300\n" +
                        "99.0 300\n" +
                        "98.0 400\n" +
                        "97.0 500\n" +
                        "___________________________"
                , orderMatchingEngine.dumpOrderBook("AAPL"));


        //Cancel the aggressive buy 50@102
        assertEquals(OrderStatus.CANCELLED, orderMatchingEngine.enter(new CancelOrder("AAPL", Side.BUY, 50, 102, clientName + 2, clientName + 4)));
        //Verify that top of the book changes and the sell order is removed
        assertEquals("___________________________\n" +
                        "              107.0 3000\n" +
                        "              106.0 2500\n" +
                        "              105.0 2000\n" +
                        "              104.0 1000\n" +
                        "              103.0 500 100 \n" +
                        "101.0 100 100 \n" +
                        "100.0 300\n" +
                        "99.0 300\n" +
                        "98.0 400\n" +
                        "97.0 500\n" +
                        "___________________________"
                , orderMatchingEngine.dumpOrderBook("AAPL"));

        //Send aggressive sell 100@102
        NewOrder aggressiveSell2 = new NewOrder("AAPL", Side.SELL, 100, 102, clientName + 5);
        orderMatchingEngine.enter(aggressiveSell2);
        assertEquals("___________________________\n" +
                        "              107.0 3000\n" +
                        "              106.0 2500\n" +
                        "              105.0 2000\n" +
                        "              104.0 1000\n" +
                        "              103.0 500 100 \n" +
                        "              102.0 100\n" +
                        "101.0 100 100 \n" +
                        "100.0 300\n" +
                        "99.0 300\n" +
                        "98.0 400\n" +
                        "97.0 500\n" +
                        "___________________________"
                , orderMatchingEngine.dumpOrderBook("AAPL"));

    }

    @Test
    public void singleAggressiveBuyAtTopOfBookOfOppositeSideCausingTrade() {
        assertInitialOrderBookIsCreatedAsExpectedFromMdSnapshot();
        assertTopOfBook("AAPL", 101, 103, 100, 500);
        //Send aggressive buy 100@103
        NewOrder aggressiveBuy1 = new NewOrder("AAPL", Side.BUY, 100, 103, clientName + 1);
        orderMatchingEngine.enter(aggressiveBuy1);
        assertTradeQtyAndPriceIsAsExpected(aggressiveBuy1,0,100, 103.0);
        assertEquals("___________________________\n" +
                        "              107.0 3000\n" +
                        "              106.0 2500\n" +
                        "              105.0 2000\n" +
                        "              104.0 1000\n" +
                        "              103.0 400\n" +
                        "101.0 100\n" +
                        "100.0 300\n" +
                        "99.0 300\n" +
                        "98.0 400\n" +
                        "97.0 500\n" +
                        "___________________________"
                , orderMatchingEngine.dumpOrderBook("AAPL"));

    }

    @Test
    public void singleAggressiveBuyGreaterThanWholeSellSide(){
        assertInitialOrderBookIsCreatedAsExpectedFromMdSnapshot();
        assertTopOfBook("AAPL",101,103, 100,500);
        //Send aggressive buy 10000@103 and 9000 gets filled and the remaining 1000 gets added to the buy side book
        NewOrder aggressiveBuy1 = new NewOrder("AAPL", Side.BUY, 10000, 107, clientName + 1);
        orderMatchingEngine.enter(aggressiveBuy1);
        assertTradeQtyAndPriceIsAsExpected(aggressiveBuy1,0,500,103);
        assertTradeQtyAndPriceIsAsExpected(aggressiveBuy1,1,1000,104);
        assertTradeQtyAndPriceIsAsExpected(aggressiveBuy1,2,2000,105);
        assertTradeQtyAndPriceIsAsExpected(aggressiveBuy1,3,2500,106);
        assertTradeQtyAndPriceIsAsExpected(aggressiveBuy1,4,3000,107);
        assertEquals("___________________________\n" +
                        "107.0 1000\n" +
                        "101.0 100\n" +
                        "100.0 300\n" +
                        "99.0 300\n" +
                        "98.0 400\n" +
                        "97.0 500\n"+
                        "___________________________"
                ,orderMatchingEngine.dumpOrderBook("AAPL"));
        assertTopOfBook("AAPL",107,-1, 1000,-1);


    }


    @Test
    public void multipleAggressiveBuyEatingMultipleLevelsOfOppositeSideBook() {
        assertInitialOrderBookIsCreatedAsExpectedFromMdSnapshot();
        assertTopOfBook("AAPL", 101, 103, 100, 500);
        //Send aggressive buy 100@103
        NewOrder aggressiveBuy1 = new NewOrder("AAPL", Side.BUY, 100, 103, clientName + 1);
        assertEquals(OrderStatus.NEW, orderMatchingEngine.enter(aggressiveBuy1));
        assertTradeQtyAndPriceIsAsExpected(aggressiveBuy1,0,100, 103.0);
        assertEquals("___________________________\n" +
                        "              107.0 3000\n" +
                        "              106.0 2500\n" +
                        "              105.0 2000\n" +
                        "              104.0 1000\n" +
                        "              103.0 400\n" +
                        "101.0 100\n" +
                        "100.0 300\n" +
                        "99.0 300\n" +
                        "98.0 400\n" +
                        "97.0 500\n" +
                        "___________________________"
                , orderMatchingEngine.dumpOrderBook("AAPL"));
        assertTopOfBook("AAPL", 101, 103, 100, 400);
        //Send aggressive buy 100@103
        NewOrder aggressiveBuy2 = new NewOrder("AAPL", Side.BUY, 100, 103, clientName + 2);
        assertEquals(OrderStatus.NEW, orderMatchingEngine.enter(aggressiveBuy2));
        assertTradeQtyAndPriceIsAsExpected(aggressiveBuy2,0,100, 103.0);
        assertEquals("___________________________\n" +
                        "              107.0 3000\n" +
                        "              106.0 2500\n" +
                        "              105.0 2000\n" +
                        "              104.0 1000\n" +
                        "              103.0 300\n" +
                        "101.0 100\n" +
                        "100.0 300\n" +
                        "99.0 300\n" +
                        "98.0 400\n" +
                        "97.0 500\n" +
                        "___________________________"
                , orderMatchingEngine.dumpOrderBook("AAPL"));

        //After 100@103 buy order executes the top of the book changes
        assertTopOfBook("AAPL", 101, 103, 100, 300);

        //Send big aggressive buy 2000@103 hitting up multiple sell levels
        NewOrder aggressiveBuy3 = new NewOrder("AAPL", Side.BUY, 2000, 103, clientName + 4);
        orderMatchingEngine.enter(aggressiveBuy3);

        assertEquals("___________________________\n" +
                        "              107.0 3000\n" +
                        "              106.0 2500\n" +
                        "              105.0 2000\n" +
                        "              104.0 1000\n" +
                        "103.0 1700\n" +
                        "101.0 100\n" +
                        "100.0 300\n" +
                        "99.0 300\n" +
                        "98.0 400\n" +
                        "97.0 500\n" +
                        "___________________________"
                , orderMatchingEngine.dumpOrderBook("AAPL"));

        assertTradeQtyAndPriceIsAsExpected(aggressiveBuy3,0,300, 103.0);
        assertTopOfBook("AAPL", 103, 104, 1700, 1000);

        //Send passive buy 2000@103
        NewOrder passiveBuy1 = new NewOrder("AAPL", Side.BUY, 2000, 103, clientName + 5);
        orderMatchingEngine.enter(passiveBuy1);
        assertEquals("___________________________\n" +
                        "              107.0 3000\n" +
                        "              106.0 2500\n" +
                        "              105.0 2000\n" +
                        "              104.0 1000\n" +
                        "103.0 1700 2000 \n" +
                        "101.0 100\n" +
                        "100.0 300\n" +
                        "99.0 300\n" +
                        "98.0 400\n" +
                        "97.0 500\n" +
                        "___________________________"
                , orderMatchingEngine.dumpOrderBook("AAPL"));
        assertTopOfBook("AAPL", 103, 104, 3700, 1000);

        //Replenish sell side
        NewOrder passiveSell1 = new NewOrder("AAPL", Side.SELL, 1000, 108, clientName + 6);
        orderMatchingEngine.enter(passiveSell1);
        assertEquals("___________________________\n" +
                        "              108.0 1000\n" +
                        "              107.0 3000\n" +
                        "              106.0 2500\n" +
                        "              105.0 2000\n" +
                        "              104.0 1000\n" +
                        "103.0 1700 2000 \n" +
                        "101.0 100\n" +
                        "100.0 300\n" +
                        "99.0 300\n" +
                        "98.0 400\n" +
                        "97.0 500\n" +
                        "___________________________"
                , orderMatchingEngine.dumpOrderBook("AAPL"));

        NewOrder passiveSell2 = new NewOrder("AAPL", Side.SELL, 100, 104, clientName + 7);
        orderMatchingEngine.enter(passiveSell2);
        assertEquals("___________________________\n" +
                        "              108.0 1000\n" +
                        "              107.0 3000\n" +
                        "              106.0 2500\n" +
                        "              105.0 2000\n" +
                        "              104.0 1000 100 \n" +
                        "103.0 1700 2000 \n" +
                        "101.0 100\n" +
                        "100.0 300\n" +
                        "99.0 300\n" +
                        "98.0 400\n" +
                        "97.0 500\n" +
                        "___________________________"
                , orderMatchingEngine.dumpOrderBook("AAPL"));

    }

    @Test
    public void singleAggressiveBuyCausingFillAndSubsequentModAndCancelIsRejected() {
        assertInitialOrderBookIsCreatedAsExpectedFromMdSnapshot();
        assertTopOfBook("AAPL", 101, 103, 100, 500);

        NewOrder aggressiveBuy1 = new NewOrder("AAPL", Side.BUY, 100, 103, clientName + 8);
        orderMatchingEngine.enter(aggressiveBuy1);
        assertTradeQtyAndPriceIsAsExpected(aggressiveBuy1,0,100, 103.0);
        assertEquals("___________________________\n" +
                        "              107.0 3000\n" +
                        "              106.0 2500\n" +
                        "              105.0 2000\n" +
                        "              104.0 1000\n" +
                        "              103.0 400\n" +
                        "101.0 100\n" +
                        "100.0 300\n" +
                        "99.0 300\n" +
                        "98.0 400\n" +
                        "97.0 500\n" +
                        "___________________________"
                , orderMatchingEngine.dumpOrderBook("AAPL"));


        assertEquals(OrderStatus.REJECTED, orderMatchingEngine.enter(new AmendOrder("AAPL", Side.BUY, 200, 103, clientName + 9, clientName + 8)));
        assertEquals(OrderStatus.REJECTED, orderMatchingEngine.enter(new CancelOrder("AAPL", Side.SELL, 50, 101, clientName + 10, clientName + 8)));
        assertEquals("___________________________\n" +
                        "              107.0 3000\n" +
                        "              106.0 2500\n" +
                        "              105.0 2000\n" +
                        "              104.0 1000\n" +
                        "              103.0 400\n" +
                        "101.0 100\n" +
                        "100.0 300\n" +
                        "99.0 300\n" +
                        "98.0 400\n" +
                        "97.0 500\n" +
                        "___________________________"
                , orderMatchingEngine.dumpOrderBook("AAPL"));
    }

    @Test
    public void multipleAggressiveSellAtTopOfBookCausingTradeFollowedByQtyAddedToListAtSamePriceLevel() {
        assertInitialOrderBookIsCreatedAsExpectedFromMdSnapshot();
        assertTopOfBook("AAPL", 101, 103, 100, 500);
        //Send sell order to cross the spread 50@101 to hit the buy order 100@101 to reduce it to 50@101
        NewOrder aggressiveSell1 = new NewOrder("AAPL", Side.SELL, 50, 101, clientName + 10);
        orderMatchingEngine.enter(aggressiveSell1);
        assertTradeQtyAndPriceIsAsExpected(aggressiveSell1,0,50, 101.0);
        assertEquals("___________________________\n" +
                        "              107.0 3000\n" +
                        "              106.0 2500\n" +
                        "              105.0 2000\n" +
                        "              104.0 1000\n" +
                        "              103.0 500\n" +
                        "101.0 50\n" +
                        "100.0 300\n" +
                        "99.0 300\n" +
                        "98.0 400\n" +
                        "97.0 500\n" +
                        "___________________________"
                , orderMatchingEngine.dumpOrderBook("AAPL"));

        assertTopOfBook("AAPL", 101, 103, 50, 500);

        //Send aggressive sell order 100@103
        NewOrder aggressiveSell2 = new NewOrder("AAPL", Side.SELL, 100, 103, clientName + 11);
        orderMatchingEngine.enter(aggressiveSell2);
        assertEquals("___________________________\n" +
                        "              107.0 3000\n" +
                        "              106.0 2500\n" +
                        "              105.0 2000\n" +
                        "              104.0 1000\n" +
                        "              103.0 500 100 \n" +
                        "101.0 50\n" +
                        "100.0 300\n" +
                        "99.0 300\n" +
                        "98.0 400\n" +
                        "97.0 500\n" +
                        "___________________________"
                , orderMatchingEngine.dumpOrderBook("AAPL"));
        assertTopOfBook("AAPL", 101, 103, 50, 600);

        //Send even more aggressive sell order 100@102
        NewOrder aggressiveSell3 = new NewOrder("AAPL", Side.SELL, 100, 102, clientName + 12);
        orderMatchingEngine.enter(aggressiveSell3);
        assertTopOfBook("AAPL", 101, 102, 50, 100);
        assertEquals("___________________________\n" +
                        "              107.0 3000\n" +
                        "              106.0 2500\n" +
                        "              105.0 2000\n" +
                        "              104.0 1000\n" +
                        "              103.0 500 100 \n" +
                        "              102.0 100\n" +
                        "101.0 50\n" +
                        "100.0 300\n" +
                        "99.0 300\n" +
                        "98.0 400\n" +
                        "97.0 500\n" +
                        "___________________________"
                , orderMatchingEngine.dumpOrderBook("AAPL"));

    }

    @Test
    public void passiveSellAtSameLevelAsBestAskQtyGetsAddedAtEndOfList() {
        assertInitialOrderBookIsCreatedAsExpectedFromMdSnapshot();
        assertTopOfBook("AAPL", 101, 103, 100, 500);
        assertEquals("___________________________\n" +
                        "              107.0 3000\n" +
                        "              106.0 2500\n" +
                        "              105.0 2000\n" +
                        "              104.0 1000\n" +
                        "              103.0 500\n" +
                        "101.0 100\n" +
                        "100.0 300\n" +
                        "99.0 300\n" +
                        "98.0 400\n" +
                        "97.0 500\n" +
                        "___________________________"
                , orderMatchingEngine.dumpOrderBook("AAPL"));
        //Send a passive sell order at best ask(50@103)
        NewOrder passiveSell1 = new NewOrder("AAPL", Side.SELL, 50, 103, clientName + 13);
        orderMatchingEngine.enter(passiveSell1);
        assertEquals("___________________________\n" +
                        "              107.0 3000\n" +
                        "              106.0 2500\n" +
                        "              105.0 2000\n" +
                        "              104.0 1000\n" +
                        "              103.0 500 50 \n" +
                        "101.0 100\n" +
                        "100.0 300\n" +
                        "99.0 300\n" +
                        "98.0 400\n" +
                        "97.0 500\n" +
                        "___________________________"
                , orderMatchingEngine.dumpOrderBook("AAPL"));
        assertNull(orderMatchingEngine.getTrades(passiveSell1));
        //Verify that the top of book price remains the same at 103
        assertTopOfBook("AAPL", 101, 103, 100, 550);
    }

    @Test
    public void passiveBuyAtSameLevelAsBestAskQtyGetsAddedInListIfAtSamePriceLevel() {
        assertInitialOrderBookIsCreatedAsExpectedFromMdSnapshot();
        assertTopOfBook("AAPL", 101, 103, 100, 500);
        assertEquals("___________________________\n" +
                        "              107.0 3000\n" +
                        "              106.0 2500\n" +
                        "              105.0 2000\n" +
                        "              104.0 1000\n" +
                        "              103.0 500\n" +
                        "101.0 100\n" +
                        "100.0 300\n" +
                        "99.0 300\n" +
                        "98.0 400\n" +
                        "97.0 500\n" +
                        "___________________________"
                , orderMatchingEngine.dumpOrderBook("AAPL"));
        //Send a passive buy order at best bid(50@103)
        NewOrder passiveBuy1 = new NewOrder("AAPL", Side.BUY, 50, 101, clientName + 14);
        orderMatchingEngine.enter(passiveBuy1);
        assertEquals("___________________________\n" +
                        "              107.0 3000\n" +
                        "              106.0 2500\n" +
                        "              105.0 2000\n" +
                        "              104.0 1000\n" +
                        "              103.0 500\n" +
                        "101.0 100 50 \n" +
                        "100.0 300\n" +
                        "99.0 300\n" +
                        "98.0 400\n" +
                        "97.0 500\n" +
                        "___________________________"
                , orderMatchingEngine.dumpOrderBook("AAPL"));
        assertNull(orderMatchingEngine.getTrades(passiveBuy1));
    }


    @Test
    public void passiveSellFollowedByCancelAndVerifyOrderBookStaysIntactAsBefore() {
        assertInitialOrderBookIsCreatedAsExpectedFromMdSnapshot();
        assertTopOfBook("AAPL", 101, 103, 100, 500);
        //Send a passive sell 50@102
        NewOrder passiveSell1 = new NewOrder("AAPL", Side.SELL, 50, 102, clientName + 15);
        orderMatchingEngine.enter(passiveSell1);
        assertEquals("___________________________\n" +
                        "              107.0 3000\n" +
                        "              106.0 2500\n" +
                        "              105.0 2000\n" +
                        "              104.0 1000\n" +
                        "              103.0 500\n" +
                        "              102.0 50\n" +
                        "101.0 100\n" +
                        "100.0 300\n" +
                        "99.0 300\n" +
                        "98.0 400\n" +
                        "97.0 500\n" +
                        "___________________________"
                , orderMatchingEngine.dumpOrderBook("AAPL"));

        assertNull(orderMatchingEngine.getTrades(passiveSell1));

        assertTopOfBook("AAPL", 101, 102, 100, 50);

        //Cancel the sell order(50@102)
        assertEquals(OrderStatus.CANCELLED, orderMatchingEngine.enter(new CancelOrder("AAPL", Side.SELL, 50, 102, clientName + 15, clientName + 16)));
        //Verify that top of the book changes and the sell order is removed
        assertEquals("___________________________\n" +
                        "              107.0 3000\n" +
                        "              106.0 2500\n" +
                        "              105.0 2000\n" +
                        "              104.0 1000\n" +
                        "              103.0 500\n" +
                        "101.0 100\n" +
                        "100.0 300\n" +
                        "99.0 300\n" +
                        "98.0 400\n" +
                        "97.0 500\n" +
                        "___________________________"
                , orderMatchingEngine.dumpOrderBook("AAPL"));
        assertTopOfBook("AAPL", 101, 103, 100, 500);

    }

    @Test
    public void passiveSellFollowedByModifyingItToAggressiveCausingFill() {
        assertInitialOrderBookIsCreatedAsExpectedFromMdSnapshot();
        assertTopOfBook("AAPL", 101, 103, 100, 500);

        //Send a passive sell order at best ask(50@103)
        NewOrder passiveSell1 = new NewOrder("AAPL", Side.SELL, 50, 103, clientName + 17);
        orderMatchingEngine.enter(passiveSell1);
        assertEquals("___________________________\n" +
                        "              107.0 3000\n" +
                        "              106.0 2500\n" +
                        "              105.0 2000\n" +
                        "              104.0 1000\n" +
                        "              103.0 500 50 \n" +
                        "101.0 100\n" +
                        "100.0 300\n" +
                        "99.0 300\n" +
                        "98.0 400\n" +
                        "97.0 500\n" +
                        "___________________________"
                , orderMatchingEngine.dumpOrderBook("AAPL"));
        assertNull(orderMatchingEngine.getTrades(passiveSell1));

        //Verify that the top of book price remains the same at 103
        assertTopOfBook("AAPL", 101, 103, 100, 550);

        //Modify the passive sell order (50@103) to aggressive sell order (100@101)
        AmendOrder passiveSellAmend = new AmendOrder("AAPL", Side.SELL, 100, 101, clientName + 17, clientName + 18);
        assertEquals(OrderStatus.AMENDED, orderMatchingEngine.enter(passiveSellAmend));
        assertEquals("___________________________\n" +
                        "              107.0 3000\n" +
                        "              106.0 2500\n" +
                        "              105.0 2000\n" +
                        "              104.0 1000\n" +
                        "              103.0 500\n" +
                        "100.0 300\n" +
                        "99.0 300\n" +
                        "98.0 400\n" +
                        "97.0 500\n" +
                        "___________________________"
                , orderMatchingEngine.dumpOrderBook("AAPL"));
        assertEquals(100, orderMatchingEngine.getTrades("AAPL",clientName+17).get(0).getTradeQty());
        assertEquals(101.0, orderMatchingEngine.getTrades("AAPL",clientName+17).get(0).getTradePrice(), 0);

        //Verify that the top of book price changes to 104
        assertTopOfBook("AAPL", 100, 103, 300, 500);

        //Send a passive sell order 100@105 at level 2 of sell side and the quantity gets added at end of list
        NewOrder passiveSell2 = new NewOrder("AAPL", Side.SELL, 100, 105, clientName + 19);
        orderMatchingEngine.enter(passiveSell2);
        assertEquals("___________________________\n" +
                        "              107.0 3000\n" +
                        "              106.0 2500\n" +
                        "              105.0 2000 100 \n" +
                        "              104.0 1000\n" +
                        "              103.0 500\n" +
                        "100.0 300\n" +
                        "99.0 300\n" +
                        "98.0 400\n" +
                        "97.0 500\n" +
                        "___________________________"
                , orderMatchingEngine.dumpOrderBook("AAPL"));

        //Verify that the top of book price stays at the same
        assertTopOfBook("AAPL", 100, 103, 300, 500);
        //Now assume we randomly get the next market snapshot and the market has moved
        orderMatchingEngine.onMdSnapshot(new MarketDataBookSnapshot("AAPL", prices, quantities));
        //We extract our original client order and add it back to the order book from the latest market data snapshot, so that it can potentially get filled later on
        assertEquals("___________________________\n" +
                        "              107.0 3000\n" +
                        "              106.0 2500\n" +
                        "              105.0 2000 100 \n" +
                        "              104.0 1000\n" +
                        "              103.0 500\n" +
                        "101.0 100\n" +
                        "100.0 300\n" +
                        "99.0 300\n" +
                        "98.0 400\n" +
                        "97.0 500\n" +
                        "___________________________"
                , orderMatchingEngine.dumpOrderBook("AAPL"));

        //Send a passive sell order 100@105 at level 3 of sell side and the quantity gets added at end of list
        NewOrder passiveSell3 = new NewOrder("AAPL", Side.SELL, 200, 105, clientName + 20);
        orderMatchingEngine.enter(passiveSell3);
        assertEquals("___________________________\n" +
                        "              107.0 3000\n" +
                        "              106.0 2500\n" +
                        "              105.0 2000 100 200 \n" +
                        "              104.0 1000\n" +
                        "              103.0 500\n" +
                        "101.0 100\n" +
                        "100.0 300\n" +
                        "99.0 300\n" +
                        "98.0 400\n" +
                        "97.0 500\n" +
                        "___________________________"
                , orderMatchingEngine.dumpOrderBook("AAPL"));

        //Send a passive sell order 100@105 at level 4 of sell side and the quantity gets added at end of list
        NewOrder passiveSell4 = new NewOrder("AAPL", Side.SELL, 200, 106, clientName + 21);
        orderMatchingEngine.enter(passiveSell4);
        assertEquals("___________________________\n" +
                        "              107.0 3000\n" +
                        "              106.0 2500 200 \n" +
                        "              105.0 2000 100 200 \n" +
                        "              104.0 1000\n" +
                        "              103.0 500\n" +
                        "101.0 100\n" +
                        "100.0 300\n" +
                        "99.0 300\n" +
                        "98.0 400\n" +
                        "97.0 500\n" +
                        "___________________________"
                , orderMatchingEngine.dumpOrderBook("AAPL"));

        //Cancel the passive sell order
        orderMatchingEngine.enter(new CancelOrder("AAPL", Side.SELL, 200, 106, clientName + 21, clientName + 22));
        assertEquals("___________________________\n" +
                        "              107.0 3000\n" +
                        "              106.0 2500\n" +
                        "              105.0 2000 100 200 \n" +
                        "              104.0 1000\n" +
                        "              103.0 500\n" +
                        "101.0 100\n" +
                        "100.0 300\n" +
                        "99.0 300\n" +
                        "98.0 400\n" +
                        "97.0 500\n" +
                        "___________________________"
                , orderMatchingEngine.dumpOrderBook("AAPL"));

        //Send big aggressive buy order and 4 levels of the sell levels are all hit
        NewOrder aggressiveBuy1 = new NewOrder("AAPL", Side.BUY, 9000, 107, clientName + 23);
        orderMatchingEngine.enter(aggressiveBuy1);
        assertEquals("___________________________\n" +
                        "              107.0 300\n" +
                        "101.0 100\n" +
                        "100.0 300\n" +
                        "99.0 300\n" +
                        "98.0 400\n" +
                        "97.0 500\n" +
                        "___________________________"
                , orderMatchingEngine.dumpOrderBook("AAPL"));

    }


    private void assertTradeQtyAndPriceIsAsExpected(Order order,int index,int quantity, double price) {
        assertEquals(quantity, orderMatchingEngine.getTrades(order).get(index).getTradeQty());
        assertEquals(price, orderMatchingEngine.getTrades(order).get(index).getTradePrice(), 0);
    }

    private void assertTopOfBook(String symbol, double bestBidP, double bestAskP, int bestBidQ, int bestAskQ) {
        TopOfBook expectedTopOfBook = new TopOfBook(symbol, bestBidP, bestAskP, bestBidQ, bestAskQ);
        TopOfBook actualTopOfBook = orderMatchingEngine.getTopOfBook(symbol);
        assertEquals(expectedTopOfBook, actualTopOfBook);
    }


}