package org.wwscc.fxchallenge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.wwscc.storage.Challenge;
import org.wwscc.storage.Database;
import org.wwscc.storage.Dialins;
import org.wwscc.storage.Entrant;
import org.wwscc.util.NF;

import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.CheckBoxTableCell;

public class LoadEntrantsDialog extends Dialog<List<DialinEntry>>
{
    private static Logger log = Logger.getLogger(LoadEntrantsDialog.class.getCanonicalName());

    public LoadEntrantsDialog(Challenge challenge)
    {
        try {
            Controller controller = new Controller(challenge, getDialogPane());  // fxloader can't find inner static classes
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fx/loadentrantsdialog.fxml"));
            loader.setController(controller);
            Parent root = (Parent)loader.load();

            setTitle("Load Entrants");
            setResizable(true);
            getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            getDialogPane().setContent(root);
            getDialogPane().lookupButton(ButtonType.OK).setDisable(true);
            getDialogPane().setPrefSize(600, 650);

            setResultConverter(dialogButton -> {
                if (dialogButton == ButtonType.OK) {
                    return controller.getResults();
                }
                return null;
            });
        } catch (Exception e) {
            log.log(Level.WARNING, "\bFailed to build dialog: " + e, e);
        }
    }


    final static class DoubleCell extends TableCell<DialinEntry, Double>
    {
        @Override
        protected void updateItem(Double val, boolean empty)
        {
            super.updateItem(val, empty);
            if (empty) {
                setText(null);
            } else {
                setText(NF.format(val));
            }
        }
    }

    public static class Controller
    {
        @FXML Label selectedCount;
        @FXML Label maxCount;
        @FXML CheckBox ladiesCheck;
        @FXML CheckBox openCheck;
        @FXML CheckBox bonusCheck;

        @FXML TableView<DialinEntry> table;
        @FXML TableColumn<DialinEntry, Boolean> selectColumn;
        @FXML TableColumn<DialinEntry, Integer> positionColumn;
        @FXML TableColumn<DialinEntry, String> firstNameColumn;
        @FXML TableColumn<DialinEntry, String> lastNameColumn;
        @FXML TableColumn<DialinEntry, String> classCodeColumn;
        @FXML TableColumn<DialinEntry, Double> netColumn;
        @FXML TableColumn<DialinEntry, Double> dialinColumn;
        @FXML TableColumn<DialinEntry, Integer> diffPositionColumn;
        @FXML TableColumn<DialinEntry, Double> diffColumn;

        Challenge target;
        DialogPane pane;


        public Controller(Challenge challenge, DialogPane dialog)
        {
            this.target = challenge;
            this.pane = dialog;
        }

        public List<DialinEntry> getResults()
        {
            return table.getItems().stream().filter(de -> de.selected.get()).collect(Collectors.toList());
        }

        @FXML
        public void initialize()
        {
               selectColumn.setCellValueFactory(cellData -> { return cellData.getValue().selected; });
             positionColumn.setCellValueFactory(cellData -> { return cellData.getValue().position.asObject(); });
            firstNameColumn.setCellValueFactory(cellData -> { return cellData.getValue().first; });
             lastNameColumn.setCellValueFactory(cellData -> { return cellData.getValue().last; });
            classCodeColumn.setCellValueFactory(cellData -> { return cellData.getValue().classCode; });
                  netColumn.setCellValueFactory(cellData -> { return cellData.getValue().net.asObject(); });
               dialinColumn.setCellValueFactory(cellData -> { return cellData.getValue().dialin.asObject(); });
         diffPositionColumn.setCellValueFactory(cellData -> { return cellData.getValue().diffposition.asObject(); });
                 diffColumn.setCellValueFactory(cellData -> { return cellData.getValue().classdiff.asObject(); });

               selectColumn.setCellFactory(tc -> new CheckBoxTableCell<>());
                  netColumn.setCellFactory(tc -> new DoubleCell());
                 diffColumn.setCellFactory(tc -> new DoubleCell());
               dialinColumn.setCellFactory(tc -> new DoubleCell());

               maxCount.setText("/ " + target.getMaxEntrantCount());
               openCheck.setSelected(true);
               ladiesCheck.setSelected(true);
               controlChange(null);
        }

        public void selectChange()
        {
            long cnt = table.getItems().stream().filter(de -> de.selected.get()).count();
            selectedCount.setText(""+cnt);
            pane.lookupButton(ButtonType.OK).setDisable(cnt < target.getMinEntrantCount() || cnt > target.getMaxEntrantCount());
        }

        public void controlChange(ActionEvent ae)
        {
            Map<UUID, Entrant> entrants = new HashMap<UUID, Entrant>();
            for (Entrant e : Database.d.getEntrantsByEvent(target.getEventId()))
            {
                if ((ladiesCheck.isSelected() && (e.getClassCode().startsWith("L"))) ||
                    (openCheck.isSelected() && (!e.getClassCode().startsWith("L"))))
                    entrants.put(e.getCarId(), e);
            }

            List<DialinEntry> data = new ArrayList<DialinEntry>();
            Dialins dialins = Database.d.loadDialins(target.getEventId());
            List<UUID> dpos = dialins.getDiffOrder();
            int pos = 1;
            for (UUID id : dialins.getNetOrder())
            {
                if (!entrants.containsKey(id))
                    continue;
                DialinEntry de = new DialinEntry(entrants.get(id), 
                                    pos, dialins.getNet(id), dialins.getDial(id, bonusCheck.isSelected()), 
                                    dpos.indexOf(id) + 1, dialins.getDiff(id));
                data.add(de);
                de.selected.addListener(e -> selectChange());
                pos++;
            }

            table.setItems(FXCollections.observableArrayList(data));
        }
    }
}