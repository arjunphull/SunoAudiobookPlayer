package com.github.arjunphull.sunoaudiobookplayer.util;

// Triplet class
public class Triplet<U, V, T>
{
    public final U first;
    public final V second;
    public final T third;

    private Triplet(U first, V second, T third)
    {
        this.first = first;
        this.second = second;
        this.third = third;
    }

    @Override
    public boolean equals(Object o)
    {
        /* Checks specified object is "equal to" the current object or not */

        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Triplet triplet = (Triplet) o;

        // call `equals()` method of the underlying objects
        if (!first.equals(triplet.first) ||
                !second.equals(triplet.second) ||
                !third.equals(triplet.third)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        /* Computes hash code for an object by using hash codes of
        the underlying objects */

        int result = first.hashCode();
        result = 31 * result + second.hashCode();
        result = 31 * result + third.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "(" + first + ", " + second + ", " + third + ")";
    }

    public static <U, V, T> Triplet <U, V, T> create(U a, V b, T c) {
        return new Triplet <>(a, b, c);
    }
}
