
package org.jgroups.util;


import java.io.*;



public class Range implements Externalizable, Streamable, Comparable<Range> {
    public long low=-1;  // first msg to be retransmitted
    public long high=-1; // last msg to be retransmitted



    /** For externalization */
    public Range() {
    }

    public Range(long low, long high) {
        this.low=low; this.high=high;
    }


    public String toString() {
        return "[" + low + " : " + high + ']';
    }


    public int compareTo(Range other) {
        if(low == other.low && high == other.high)
            return 0;
        return low < other.low? -1 : 1;
    }

    public int hashCode() {
        return (int)low;
    }

    public boolean equals(Object obj) {
        Range other=(Range)obj;
        return compareTo(other) == 0;
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeLong(low);
        out.writeLong(high);
    }


    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        low=in.readLong();
        high=in.readLong();
    }


    public void writeTo(DataOutput out) throws IOException {
        out.writeLong(low);
        out.writeLong(high);
    }

    public void readFrom(DataInput in) throws IOException, IllegalAccessException, InstantiationException {
        low=in.readLong();
        high=in.readLong();
    }


}
