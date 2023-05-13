package functions;
import java.util.ArrayList;
import java.util.List;

public class PrimeNumbers {

    public static List<Integer> primeNumbers(int n) {

        List<Integer> result = new ArrayList<>();

        for (int i = 2; i <= n; i++) {
            if (isPrime(i)) {
                result.add(i);
            }
        }

        return result;
    }

    public static boolean isPrime(int n) {

        if (n < 2) {
            return false;
        }

        for (int i = 2; i <= Math.sqrt(n); i++) {
            if (n % i == 0) {
                return false;
            }
        }

        return true;
    }

    public static int sumPrimeNumbers(int n) {
        List<Integer> primes = primeNumbers(n);
        int sum = 0;

        for (int prime : primes) {
            sum += prime;
        }

        return sum;
    }
}

