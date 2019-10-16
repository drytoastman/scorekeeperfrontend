package org.wwscc.dataentry.tables;

import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wwscc.dataentry.DataEntry;
import org.wwscc.storage.Car;
import org.wwscc.storage.ClassData;
import org.wwscc.storage.Database;
import org.wwscc.storage.DecoratedCar;
import org.wwscc.storage.Driver;
import org.wwscc.storage.Entrant;
import org.wwscc.storage.FakeDatabase;
import org.wwscc.util.AppSetup;
import org.wwscc.util.EventInfo;
import org.wwscc.util.MT;
import org.wwscc.util.MessageListener;
import org.wwscc.util.Messenger;

public class ScannedBarcodesTest
{
    DatabaseShim shim;
    DoubleTableContainer totest;
    static Car added;

    static MessageListener listener = new MessageListener() {
        @Override
        public void event(MT type, Object data) {
            if (type == MT.CAR_ADD) {
                added = (Car)data;
            }
        }
    };

    class TestEntrant extends Entrant {
        public void setCar(Car c) {
            car = c;
        }
    }

    class TestDecoratedCar extends DecoratedCar {
        boolean paid = false;
        public TestDecoratedCar(Car c, boolean p) {
            super(c);
            paid = p;
        }
        public boolean hasPaid() { return paid; }
    }

    class DatabaseShim extends FakeDatabase {
        int newdriver = 0;
        int newcar = 0;
        int registered = 0;
        List<Driver> drivers = new ArrayList<Driver>();
        Map<UUID, TestDecoratedCar> cars = new HashMap<UUID, TestDecoratedCar>();
        ClassData classdata = new ClassData();

        public DatabaseShim() { classdata.add(new ClassData.Class("i1", false)); }
        @Override public void newDriver(Driver d) throws Exception { newdriver++; }
        @Override public void newCar(Car c) throws Exception { newcar++; }
        @Override public void registerCar(UUID eventid, UUID carid) throws Exception { registered++; }
        @Override public List<Driver> findDriverByBarcode(String barcode) { return drivers; }
        @Override public List<Car> getRegisteredCars(UUID driverid, UUID eventid) { return new ArrayList<Car>(cars.values()); }
        @Override public String getEffectiveIndexStr(Car c) { return "i1"; }
        @Override public ClassData getClassData() { return classdata; }
        @Override public DecoratedCar decorateCar(Car c, UUID eventid, int course) { return (TestDecoratedCar)c; }
        @Override public Entrant loadEntrant(UUID eventid, UUID carid, int course, boolean loadruns) {
            for (Car c : cars.values()) {
                if (c.getCarId().equals(carid)) {
                    TestEntrant e = new TestEntrant();
                    e.setCar(c);
                    return e;
                }
            }
            return null;
        }
    }

    @BeforeClass
    public static void init() {
        AppSetup.unitLogging();
        Messenger.setTestMode();
        Messenger.register(MT.CAR_ADD, listener);
    }

    @AfterClass
    public static void fini() throws Exception {
        Messenger.unregister(MT.CAR_ADD, listener);
    }

    @Before
    public void setUp() throws Exception
    {
        shim = new DatabaseShim();
        Database.d = shim;
        DataEntry.state.setCurrentEvent(new EventInfo());

        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
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
        TestDecoratedCar c = new TestDecoratedCar(new Car(), false);
        shim.cars.put(c.getCarId(), c);
        totest.processBarcode("12345");
        Assert.assertEquals(1, shim.newdriver);
        Assert.assertEquals(1, shim.newcar);
        Assert.assertEquals(1, shim.registered);
    }

    @Test
    public void testPaidSelection()
    {
        Driver d = new Driver();
        TestDecoratedCar c1 = new TestDecoratedCar(new Car(), false);
        TestDecoratedCar c2 = new TestDecoratedCar(new Car(), true);
        c1.setDriverId(d.getDriverId());
        c2.setDriverId(d.getDriverId());
        c1.setClassCode("i1");
        c2.setClassCode("i1");
        shim.drivers.add(d);
        shim.cars.put(c1.getCarId(), c1);
        shim.cars.put(c2.getCarId(), c2);
        totest.dataModel.tableData = new ArrayList<>();
        totest.driverScanned(d);
        Assert.assertEquals(c2, ((Entrant)totest.dataModel.getValueAt(0, 0)).getCar());
    }
}
