/*
 * This software is licensed under the GPLv3 license, included as
 * ./GPLv3-LICENSE.txt in the source distribution.
 *
 * Portions created by Brett Wilson are Copyright 2008 Brett Wilson.
 * All rights reserved.
 */

package org.wwscc.dialogs;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import net.miginfocom.swing.MigLayout;
import org.wwscc.storage.Car;
import org.wwscc.storage.ClassData;
import org.wwscc.storage.Database;
import org.wwscc.util.TextChangeTrigger;


/**
 * Core functions for all dialogs.
 */
public class CarDialog extends BaseDialog<Car>
{
    private static Logger log = Logger.getLogger(CarDialog.class.getCanonicalName());

    protected JButton add;
    protected boolean addToRunOrder;
    protected JCheckBox override;
    protected JCheckBox classmult;
    protected UUID driverid;
    protected StatusIcon numberok;
    protected List<Integer> usednumbers;
    protected List<ClassData.Index> indexlist;
    protected ActionListener classChange;
    protected ActionListener indexChange;

    /**
     * Create the dialog.
     * @param car		the initial car data to use
     * @param cd		the classdata to use for classes/indexes
     * @param addoption whether to add the create and add button
     */
    public CarDialog(UUID did, Car car, ClassData cd, boolean addoption)
    {
        super(new MigLayout("fillx, gap 5", "[right, grow 0][grow 100, fill, 130]"), true);

        driverid = did;
        if (car == null)
            car = new Car();

        addToRunOrder = false;
        if (addoption)
        {
            add = new JButton("Create and Add");
            add.addActionListener(this);
            buttonPanel.add(add, 0);
            defaultButton = add;
        }
        else
        {
            defaultButton = ok;
        }

        ok.setText("Create");

        mainPanel.add(label("Year", false), "");
        mainPanel.add(entry("year", car.getYear()), "wrap");

        Map<String, Set<String>> attr = Database.d.getCarAttributes();

        List<String> makes = new ArrayList<String>(attr.get("make"));
        Collections.sort(makes);
        mainPanel.add(label("Make", false), "");
        mainPanel.add(autoentry("make", car.getMake(), makes), "wrap");

        List<String> models = new ArrayList<String>(attr.get("model"));
        Collections.sort(models);
        mainPanel.add(label("Model", false), "");
        mainPanel.add(autoentry("model", car.getModel(), models), "wrap");

        List<String> colors = new ArrayList<String>(attr.get("color"));
        Collections.sort(colors);
        mainPanel.add(label("Color", false), "");
        mainPanel.add(autoentry("color", car.getColor(), colors), "wrap");

        List<ClassData.Class> classlist = cd.getClasses();
        classlist.removeIf(item -> item.getCode().equals("HOLD"));
        indexlist = cd.getIndexes();
        Collections.sort(classlist, new ClassData.Class.StringOrder());
        Collections.sort(indexlist, new ClassData.Index.StringOrder());
        classChange = new ClassChange();
        indexChange = new IndexChange();

        mainPanel.add(label("Class", true), "");
        mainPanel.add(select("classcode", cd.getClass(car.getClassCode()), classlist, classChange), "wrap");

        mainPanel.add(label("Index", true), "");
        mainPanel.add(select("indexcode", cd.getIndex(car.getIndexCode()), indexlist, indexChange), "wrap");

        mainPanel.add(label("Number", true), "");
        mainPanel.add(ientry("number", (car.getNumber()>0)?car.getNumber():null), "split, growx");
        numberok = new StatusIcon();
        mainPanel.add(new JLabel(numberok), "wrap");
        fields.get("number").getDocument().addDocumentListener(new TextChangeTrigger() { @Override public void changedTo(String txt) { checkNumberUse(); }});

        override = new JCheckBox("Override Index Restrictions");
        override.setToolTipText("Some classes restrict the available indexes, this lets you override that restriction, only for use in rare circumstances");
        override.addActionListener(this);
        override.setEnabled(false);
        mainPanel.add(override, "skip, gaptop 10, wrap");

        classmult = new JCheckBox("Class Multiplier Flag");
        classmult.addActionListener(this);
        classmult.setEnabled(false);
        mainPanel.add(classmult, "skip, gapbottom 10, wrap");

        classChange.actionPerformed(new ActionEvent(this, 1, ""));
        result = car;
    }

    class StatusIcon implements Icon
    {
        private int size = 16;
        private Color color = Color.gray;
        @Override public int getIconWidth() { return size; }
        @Override public int getIconHeight() { return size; }
        @Override public void paintIcon(Component c, Graphics g, int x, int y)
        {
            g.setColor(color);
            g.fillRect(x, y, size, size);
            g.setColor(Color.DARK_GRAY);
            g.drawRect(x, y, size-1, size-1);
        }
        public void setUnknown() { color = Color.gray; repaint(); }
        public void setOk()      { color = Color.green; repaint(); }
        public void setWarning() { color = Color.yellow; repaint(); }
    }

    public void setOkButtonText(String text)
    {
        ok.setText(text);
    }


    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected void updateIndexList(ClassData.Class basis)
    {
        JComboBox<?> indexes = selects.get("indexcode");
        if (indexes.isVisible())
        {
            Object selected = indexes.getSelectedItem();
            if (override.isSelected() || (basis == null))
            {
                indexes.setModel(new DefaultComboBoxModel(indexlist.toArray()));
            }
            else
            {
                Set<ClassData.Index>[] lists = basis.restrictedIndexes(indexlist);
                ClassData.Index[] allowed = lists[0].toArray(new ClassData.Index[0]);
                Arrays.sort(allowed, new ClassData.Index.StringOrder());
                indexes.setModel(new DefaultComboBoxModel(allowed));
            }

            indexes.setSelectedItem(selected);  // if index is still available, keep it selected
        }
    }


    class ClassChange implements ActionListener
    {
        public void actionPerformed(ActionEvent ae)
        {
            ClassData.Class c = (ClassData.Class)selects.get("classcode").getSelectedItem();
            if (c == null)
            {
                labels.get("Index").setEnabled(false);
                selects.get("indexcode").setEnabled(false);
                selects.get("indexcode").setSelectedIndex(-1);
                classmult.setEnabled(false);
                numberok.setUnknown();
                return;
            }

            labels.get("Index").setEnabled(c.carsNeedIndex());
            selects.get("indexcode").setEnabled(c.carsNeedIndex());
            if (!c.carsNeedIndex())
                selects.get("indexcode").setSelectedIndex(-1);
            override.setEnabled(c.carsNeedIndex());
            usednumbers = Database.d.getUnavailableNumbers(driverid, c.getCode());
            checkNumberUse();
            updateIndexList(c);
            indexChange.actionPerformed(new ActionEvent(this, 1, ""));
        }
    }


    class IndexChange implements ActionListener
    {
        @SuppressWarnings("unchecked")
        public void actionPerformed(ActionEvent ae)
        {
            ClassData.Class basis = (ClassData.Class)selects.get("classcode").getSelectedItem();
            if ((basis != null) && basis.useCarFlag())
            {
                ClassData.Index current = (ClassData.Index)selects.get("indexcode").getSelectedItem();
                Set<ClassData.Index>[] lists = basis.restrictedIndexes(indexlist);
                if (override.isSelected() || lists[1].contains(current)) {
                       classmult.setEnabled(true);
                       return;
                }
            }
            classmult.setEnabled(false);
        }
    }


    public void checkNumberUse()
    {
        try {
            if ((usednumbers == null) || (!usednumbers.contains(getEntryInt("number")))) {
                numberok.setOk();
            } else {
                numberok.setWarning();
            }
        } catch (Exception e) {
            numberok.setUnknown();
        }
    }


    @Override
    public void actionPerformed(ActionEvent ae)
    {
        Object o = ae.getSource();

        if (o == override)
        {
            updateIndexList((ClassData.Class)selects.get("classcode").getSelectedItem());
        }
        else if (o == add)
        {
            addToRunOrder = true;
            ae.setSource(ok);
            super.actionPerformed(ae);
        }
        else
        {
            super.actionPerformed(ae);
        }
    }

    /**
     * Determine if the dialog wanted to add to run order
     * @return true if should directly add to runorder
     */
    public boolean getAddToRunOrder()
    {
        return addToRunOrder;
    }

    /**
     * Called after OK to verify data before closing.
     * @return true if data is valid, false otherwise
     */
    @Override
    public boolean verifyData()
    {
        try
        {
            ClassData.Class c = (ClassData.Class)getSelect("classcode");
            if (c.getCode().equals(""))
            {
                errorMessage = "Must select a class";
                return false;
            }

            if (c.carsNeedIndex())
            {
                ClassData.Index i = (ClassData.Index)getSelect("indexcode");
                if ((i == null) || i.getCode().equals(""))
                {
                    errorMessage = "Selected class requires an index";
                    return false;
                }
            }

            if (getEntryText("number").equals(""))
            {
                errorMessage = "Please enter a car number";
                return false;
            }
        }
        catch (Exception e)
        {
            errorMessage = "Error: " + e.getMessage();
            return false;
        }

        return true;
    }


    /**
     * @return if the return value is valid, a Car, null otherwise
     */
    @Override
    public Car getResult()
    {
        if (!valid)
            return null;

        try
        {
            result.setDriverId(driverid);
            result.setYear(getEntryText("year").trim());
            result.setMake(getEntryText("make").trim());
            result.setModel(getEntryText("model").trim());
            result.setColor(getEntryText("color").trim());

            ClassData.Class c = (ClassData.Class)getSelect("classcode");
            result.setClassCode(c.getCode());

            if (c.carsNeedIndex())
            {
                ClassData.Index i = (ClassData.Index)getSelect("indexcode");
                result.setIndexCode(i.getCode());
            }
            else
            {
                result.setIndexCode("");
            }

            if (c.useCarFlag())
                result.setUseClsMult(classmult.isSelected());
            else
                result.setUseClsMult(false);

            result.setNumber(Integer.parseInt(getEntryText("number")));
            return result;
        }
        catch (Exception e)
        {
            log.severe("\bBad data in CarDialog: " + e);
            return null;
        }
    }
}


