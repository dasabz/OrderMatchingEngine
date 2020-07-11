### OrderMatchingEngine(purely operating with limit orders) 

##### Requirement is that the matching engine gets a market data snapshot which is a constant stream of level 2 market data for both bids and asks. I have implemented it as per below understanding of the problem statement

#### ASSUMPTIONS AND IMPLEMENTATION CHOICES

##### The essence of this order matching engine is that it is intended to be used in a backtester environment in order to test algo behaviour. 
##### There is no guarantee that ALGO will send orders to this matching engine at only top 5 levels, however it receives only level2 market data from exchange 
##### The marketdata snapshot which the OrderMatchingEngine receives does not have individual orders list at each price level and also as it is in our environment so the market data from exchange obviously does not include our algo orders

##### The order matching engine receives marketdata snapshot upon which it builds its orderbook (buy side and sell side upto 5 sides)
##### When it receives a new/amend from the OrderRouting system(For brevity i have directly inserted into OrderMatchingEngine), it does below
#####     1. It checks if order is less aggressive than opposites side tob and if yes then directly adds it as a passive order else
#####     2. It tries to match the order with the market data book which it has received from exchange
#####     3. If it is not able to match the order fully it inserts remaining into the order book as that is actually passive according to the market at that point.
#####     4. It updates the top of the book bestbid/ask price and accumulated quantity at each level.
#####     5. It assumes that price modification and quantity amend up together will lead to loss of queue priority
#####     6. If only price amendment is done, queue priority is still lost
#####     7. If price is same and quantity is amended down, then queue priority is preserved and order is updated inplace
#####     8. If price is same and quantity is amended up, then queue priority is preserved and a new additional slice is placed at the end of queue at that price level.
#####     9. Subsequent amend down after amend up cancels the youngest slice first and thus preserves queue priority. Subsequent cancel after amend up cancels all the slices for the order.


##### When it randomly gets a new market data snapshot while it is processing its orders currently in its book then it does below
#####    1. It is possible that the ALGO is sending it a lot of passive orders at different levels of the order book, with multiple quantities at same price level 
#####    2. In order to handle this, the orderbook has 2 TreeMaps of Double and LinkedList sorted accordingly(Buy and Sell). 
#####    3. Linked list is deliberately as the orders to automatically have priority of insertion per order level, also removal of individual quantities at each level in a linked list is better than an array as other elements dont need to be adjusted
#####    4. When it gets a market data snapshot, it saves its current order book first , then it constructs its new order book from the latest market data snapshot and then whatever orders were there in its order book before the market data snapshot came, it enters them back again into the order book in the hope of getting match, as ofcourse we want to ensure even the passive orders from algo which were sent by the algo before the market data snapshot is received, , eventually can get matched hopefully.


##### DESIGN(Brief description)

##### 1. OrderMatchingEngineImpl class primarily takes care of entering new, mod, cancel requests for different symbols.As soon as we have an order, OrderMatchingEngineImpl finds the symbol pertaining to which the order is sent and accordingly matches/inserts the order to the orderbook
##### 2. OrderBook class is the main class which does the matching per symbol.
##### 3. New/Amend/Cancel orders are created individually so that if subsequently some specific attributes need to be added/handled it can be added accordingly for mod and cancel
##### 4. OrderValidatorImpl is empty. Typically order matching engine will have many market high low, limit up down , lot size, agency vs principal checks. This is left empty left open for such checks.
##### 5. TopOfBook saves the best bid/ask price and quantity encapsulated in one class
##### 6. OrderStatus/Side enums for easier readability of code
##### 7. Trade class is for generation of the trades when match happens.


##### BUILD

##### The project is using gradle for building and as long as the project is imported into Intellij, we can just click on the build task in gradle to build and go the OrderMatchingEngineTest to run it to see the behaviour


##### IMPROVEMENTS WHICH CAN BE DONE

##### 1. Quantity mod up requests when the price level changes, can also be improved to keep the existing slice at that level and create new slices at the next intended passive/aggressive level but in the interest of time for this exercise, this is not handled and queue priority is lost on price amend
##### 1. Price Quantity checks based on market data , reference data etc need to be done before matching. This is one area to improve (To add more OMS based checks for quantity/price/lot size/tick size etc)
##### 2. Test coverage maybe possible to add some more test case combinations but within the time i have added cases as appropriate.The price time priority should work as per the implementation but more test cases should be added around that to ensure modify/cancel works fine with multiple slices.
##### 3. The matching engine can be improved in performance by doing some GC analysis as well as object pools to make sure GC doesnt get triggered during runtime but that area is not explored as a part of this exercise as i mostly focussed on the business logic.
##### 4. This matching engine only operates on limit orders. It needs to be enhanced to handle market orders, plus handling for IOC/FOK orders
##### 5. The matching engine should be able to handle commands like pauseMatching, startMatching, stopMatching and react accordingly in a real life situation
##### 6. The matching engine also can be enhanced to support querying the number of orders at each level along with query for accumulated quantity at each level
##### 7. Matching engine normally will need to have some priority of agency vs prop orders where agency orders need to be give priority over prop. It can be enhanced to handle that if needed in future.
##### 8. Matching engine in certain conditions can operate both as a venue as well as a passthrough where after it gets an order, it can try to match the order and then if it cannot match it can send to the exchange and then later on if a favourable opportunity arises it may decide to pull back the order from venue and do the matching itself.
##### 9. Matching engine should be able to handle multiple symbols which is supported by the design but more test cases around multiple symbols interleaved need to be added 
##### 10. The matching engine currently only handles modifications for price and quantity, but other attribute modification also needs to be handled in reality and that can be extended