package org.wwscc.dataentry.tables;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.wwscc.dataentry.DataEntry;
import org.wwscc.storage.Car;
import org.wwscc.storage.Database;
import org.wwscc.storage.Driver;
import org.wwscc.storage.Event;
import org.wwscc.storage.FakeDatabase;

public class ScannedBarcodesTest
{
    DatabaseShim shim;
    DoubleTableContainer totest;

    class DatabaseShim extends FakeDatabase {
        int newdriver = 0;
        int newcar = 0;
        int registered = 0;
        List<Driver> drivers = new ArrayList<Driver>();
        List<Car> cars = new ArrayList<Car>();

        @Override public void newDriver(Driver d) throws Exception { newdriver++; }
        @Override public void newCar(Car c) throws Exception { newcar++; }
        @Override public void registerCar(UUID eventid, UUID carid) throws Exception { registered++; }
        @Override public List<Driver> findDriverByBarcode(String barcode) { return drivers; }
        @Override public List<Car> getRegisteredCars(UUID driverid, UUID eventid) { return cars; }
    }

    @Before
    public void setUp() throws Exception
    {
        shim = new DatabaseShim();
        Database.d = shim;
        Event e = new Event();
        DataEntry.state.setCurrentEvent(e);
        totest = new DoubleTableContainer();
    }

    @Test
    public void testPlaceholder()
    {
        Assert.assertEquals(0, shim.newdriver);
        Assert.assertEquals(0, shim.newcar);
        Assert.assertEquals(0, shim.registered);
        totest.processBarcode("12345");
        Assert.assertEquals(1, shim.newdriver);
        Assert.assertEquals(1, shim.newcar);
        Assert.assertEquals(1, shim.registered);
        shim.drivers.add(new Driver());
        shim.cars.add(new Car());
        totest.processBarcode("12345");
        Assert.assertEquals(1, shim.newdriver);
        Assert.assertEquals(1, shim.newcar);
        Assert.assertEquals(1, shim.registered);
    }
}
