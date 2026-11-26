package shop;
import java.util.List;
public class OrderProcessor {
    private double discountRate;
    /* Конструктор с установкой скидки */
    public OrderProcessor(double discountRate) {
        this.discountRate = discountRate;
    }
    public double calculateTotal(List<Double> itemPrices) {
        double rawSum = 0.0;
        for (Double price : itemPrices) {
            rawSum += price;
        }
        return rawSum * (1.0 - discountRate);
    }
    public static void main(String[] args) {
        OrderProcessor processor = new OrderProcessor(0.1);
        System.out.println(processor.calculateTotal(List.of(100.0, 250.0)));
    }
}
