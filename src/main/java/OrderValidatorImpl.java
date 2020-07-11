public class OrderValidatorImpl implements OrderValidator {
    //Left empty (Open for adding checks for high low checks, limit up down checks, stock suspension checks,lot size checks
    @Override
    public boolean isValid(Order order) {
        return true;
    }
}
