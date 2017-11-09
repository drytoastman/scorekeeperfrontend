package org.wwscc.protimer;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.wwscc.util.MT;

public class ModelTest {

    ResultsModel model;
    
    @Before
    public void start() {
        model = new ResultsModel();
    }
    
    @Test
    public void testNoTree() {
        // This should ignore the input, not throw an exception
        model.addReaction(true, new ColorTime(1));
        model.addReaction(false, new ColorTime(1));
        model.addSixty(true, new ColorTime(1));
        model.addSixty(true, new ColorTime(1));
        model.addFinish(true, new ColorTime(1), 1);
        model.addFinish(false, new ColorTime(1), 1);
        Assert.assertEquals(0, model.nextLeftFinish);
        Assert.assertEquals(0, model.nextRightFinish);
        model.deleteStart(true);
        model.deleteStart(false);
        model.deleteFinish(true);
        model.deleteFinish(false);
        Assert.assertEquals(0, model.nextLeftFinish);
        Assert.assertEquals(0, model.nextRightFinish);
        model.event(MT.INPUT_RESET_HARD, null);
        Assert.assertEquals(0, model.nextLeftFinish);
        Assert.assertEquals(0, model.nextRightFinish);
    }
    
    @Test
    public void testMultiActive() {
        model.createNewEntry();
        model.addReaction(true, new ColorTime(1));
        model.addReaction(false, new ColorTime(1));
        model.createNewEntry();
        model.addReaction(true, new ColorTime(1));
        model.addReaction(false, new ColorTime(1));
        model.createNewEntry();
        model.addReaction(true, new ColorTime(1));
        model.addReaction(false, new ColorTime(1));

        for (int ii = 1; ii <= 3; ii++) {
            model.addFinish(true, new ColorTime(1), 1);
            model.addFinish(false, new ColorTime(1), 1);
            Assert.assertEquals(ii, model.nextLeftFinish);
            Assert.assertEquals(ii, model.nextRightFinish);
        }
        
        // ignore finish, stay as 3
        model.addFinish(true, new ColorTime(1), 1);
        Assert.assertEquals(3, model.nextLeftFinish);
        Assert.assertEquals(3, model.nextRightFinish);
        
        model.event(MT.INPUT_RESET_HARD, null);
        Assert.assertEquals(3, model.nextLeftFinish);
        Assert.assertEquals(3, model.nextRightFinish);
    }
}
