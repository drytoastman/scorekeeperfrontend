package org.wwscc.fxchallenge;

public class Positions
{
    // these are the placements into the bracket as per SCCA rulebook
    static final int[] RANK4 =  new int[] { 3, 2, 4, 1 };
    static final int[] RANK8 =  new int[] { 6, 3, 7, 2, 5, 4, 8, 1 };
    static final int[] RANK16 = new int[] { 11, 6, 14, 3, 10, 7, 15, 2, 12, 5, 13, 4, 9, 8, 16, 1 };
    static final int[] RANK32 = new int[] { 22, 11, 27, 6, 19, 14, 30, 3, 23, 10, 26, 7, 18, 15, 31, 2, 21, 12, 28, 5, 20, 13, 29, 4, 24, 9, 25, 8, 17, 16, 32, 1 };

    // these are the map from finishing place to first bracket indexes
    static final int[] POS4 = new int[4];  // i.e. becomes [ 3, 1, 0, 2], so person in top position, index 0 gets put in bracket position 3
    static final int[] POS8 = new int[8];
    static final int[] POS16 = new int[16];
    static final int[] POS32 = new int[32];

    static
    { // Take the SCCA mapping and turn it into values we can use
        for (int ii = 0; ii < RANK32.length; ii++)
            POS32[RANK32[ii]-1] = ii;
        for (int ii = 0; ii < RANK16.length; ii++)
            POS16[RANK16[ii]-1] = ii;
        for (int ii = 0; ii < RANK8.length; ii++)
            POS8[RANK8[ii]-1] = ii;
        for (int ii = 0; ii < RANK4.length; ii++)
            POS4[RANK4[ii]-1] = ii;
    }
}
