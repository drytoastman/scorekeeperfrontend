package org.wwscc.fxchallenge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

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
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

public class LoadEntrantsDialog extends Dialog<List<ChallengeEntry>>
{
    private static Logger log = Logger.getLogger(LoadEntrantsDialog.class.getCanonicalName());

    public LoadEntrantsDialog(Challenge challenge)
    {
        try {
            Controller controller = new Controller(challenge, getDialogPane());  // fxloader can't find inner static classes
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/wwscc/fxchallenge/loadentrantsdialog.fxml"));
            loader.setController(controller);
            Parent root = (Parent)loader.load();

            setTitle("New Challenge");
            setResizable(true);
            getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            getDialogPane().setContent(root);
            getDialogPane().lookupButton(ButtonType.OK).setDisable(true);
            getDialogPane().setPrefSize(400, 700);

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


    final static class DoubleCell extends TableCell<ChallengeEntry, Number>
    {
        @Override
        protected void updateItem(Number val, boolean empty)
        {
            super.updateItem(val, empty);
            if (empty) {
                setText(null);
            } else {
                setText(NF.format(val.doubleValue()));
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

        @FXML TableView<ChallengeEntry> table;
        @FXML TableColumn<ChallengeEntry, Number> positionColumn;
        @FXML TableColumn<ChallengeEntry, String> firstNameColumn;
        @FXML TableColumn<ChallengeEntry, String> lastNameColumn;
        @FXML TableColumn<ChallengeEntry, String> classCodeColumn;
        @FXML TableColumn<ChallengeEntry, Number> netColumn;
        @FXML TableColumn<ChallengeEntry, Number> dialinColumn;

        Challenge target;
        DialogPane pane;


        public Controller(Challenge challenge, DialogPane dialog)
        {
            this.target = challenge;
            this.pane = dialog;
        }

        public List<ChallengeEntry> getResults()
        {
            return table.getSelectionModel().getSelectedItems();
        }

        @FXML
        public void initialize()
        {
            table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            table.getSelectionModel().selectedItemProperty().addListener((obs, olds, news) -> {
                int cnt = table.getSelectionModel().getSelectedIndices().size();
                selectedCount.setText(""+cnt);
                pane.lookupButton(ButtonType.OK).setDisable(cnt < target.getMinEntrantCount() || cnt > target.getMaxEntrantCount());
            });

             positionColumn.setCellValueFactory(cellData -> { return cellData.getValue().position; });
            firstNameColumn.setCellValueFactory(cellData -> { return cellData.getValue().first; });
             lastNameColumn.setCellValueFactory(cellData -> { return cellData.getValue().last; });
            classCodeColumn.setCellValueFactory(cellData -> { return cellData.getValue().classCode; });
                  netColumn.setCellValueFactory(cellData -> { return cellData.getValue().net; });
               dialinColumn.setCellValueFactory(cellData -> { return cellData.getValue().dialin; });

                  netColumn.setCellFactory(tc -> new DoubleCell());
               dialinColumn.setCellFactory(tc -> new DoubleCell());

               maxCount.setText("/ " + target.getMaxEntrantCount());
               openCheck.setSelected(true);
               ladiesCheck.setSelected(true);
               controlChange(null);
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

            List<ChallengeEntry> data = new ArrayList<ChallengeEntry>();
            Dialins dialins = Database.d.loadDialins(target.getEventId());
            int pos = 1;
            for (UUID id : dialins.getNetOrder())
            {
                if (!entrants.containsKey(id))
                    continue;
                data.add(new ChallengeEntry(entrants.get(id), pos, dialins.getNet(id), dialins.getDial(id, bonusCheck.isSelected())));
                pos++;
            }

            table.setItems(FXCollections.observableArrayList(data));
        }
    }

}