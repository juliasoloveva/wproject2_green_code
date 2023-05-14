package functions;
import java.math.BigInteger;

public class Factorial {
    public static BigInteger factorial(int a){
        BigInteger result = BigInteger.valueOf(1);
        for (int i = 2; i <= a; i++) {
            result = result.multiply(BigInteger.valueOf(i));
        }
        return result;
    }

    public static String cmd_fact(int n){

        if (n<0){
            return "undefined";
        }
        return String.valueOf(factorial(n));
    }
}
