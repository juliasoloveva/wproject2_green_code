package functions;

public class Factorial {
    public int factorial(int a){
        if(a<0){
            return 0;
        }
        else
        if (a<2){
            return 1;
        }
            return a*factorial(a-1);
    }

    public String cmd_fact(int n){
        return String.valueOf(factorial(n));
    }
}
