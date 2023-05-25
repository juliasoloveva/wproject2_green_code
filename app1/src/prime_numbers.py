# Corrected prime_numbers function
def prime_numbers(n):
    def is_prime(n):
        if n < 2:
            return False
        for i in range(2, int(n ** 0.5) + 1):
            if n % i == 0:
                return False
        return True

    result = []
    for i in range(n + 1):
        if is_prime(i):
            result.append(i)
    return result

# sum_prime_numbers function
def sum_prime_numbers(n):
    return sum(prime_numbers(n))

