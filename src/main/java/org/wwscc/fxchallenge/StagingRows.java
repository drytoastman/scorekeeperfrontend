package org.wwscc.fxchallenge;

import java.util.Arrays;
import org.wwscc.util.MT;
import org.wwscc.util.Messenger;

import javafx.beans.property.IntegerProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.util.Callback;

public class StagingRows implements Callback<TableView<ChallengePair>,TableRow<ChallengePair>>
{
    private static final DataFormat SERIALIZED_MIME_TYPE = new DataFormat("application/x-java-serialized-object");

    private ContextMenu contextMenu;
    private TableRow<ChallengePair> contextRow;
    private MenuItem activateRow, deactivate, removeRoundPair, clearRowData, removeHighlight;
    private IntegerProperty highlightRound;

    public StagingRows(IntegerProperty highlightRound)
    {
        this.highlightRound = highlightRound;

        activateRow = new MenuItem("Make Row Active");
        activateRow.setOnAction(e -> Messenger.sendEvent(MT.MAKE_ROW_ACTIVE, contextRow.getIndex()));

        removeRoundPair = new MenuItem("Remove Round From Staging");
        removeRoundPair.setOnAction(e -> Messenger.sendEvent(MT.REMOVE_ROUND, contextRow.getItem().round.get()));

        deactivate = new MenuItem("Deactivate");
        deactivate.setOnAction(e -> Messenger.sendEvent(MT.DEACTIVATE, null));

        clearRowData    = new MenuItem("Clear Run Data");
        clearRowData.setOnAction(e -> Messenger.sendEvent(MT.CLEAR_ROW_DATA, contextRow.getIndex()));

        removeHighlight = new MenuItem("Clear Highlight");
        removeHighlight.setOnAction(e -> { highlightRound.set(-1); });

        contextMenu = new ContextMenu();
        contextMenu.getItems().addAll(activateRow, removeRoundPair, deactivate, new SeparatorMenuItem(), clearRowData, removeHighlight);
    }


    class StagingTableRow extends TableRow<ChallengePair> implements ChangeListener<Object>
    {
        public StagingTableRow()
        {
            super();
            highlightRound.addListener(this);
        }

        private void updateCss(ChallengePair pair)
        {
            if (pair != null) {
                if (pair.round.get() == highlightRound.get()) {
                    getStyleClass().add("highlightedRow");
                } else {
                    getStyleClass().remove("highlightedRow");
                }
                if ((pair.round.get() % 2) != 0) {
                    getStyleClass().add("oddRoundRow");
                } else {
                    getStyleClass().remove("oddRoundRow");
                }
            } else {
                getStyleClass().removeAll(Arrays.asList("highlightedRow", "oddRoundRow"));
            }
        }

        public void changed(ObservableValue<? extends Object> ob, Object o1, Object n1)
        {
            updateCss(itemProperty().get());
        }

        @Override
        public void updateItem(ChallengePair pair, boolean empty)
        {
            super.updateItem(pair, empty);
            updateCss(pair);
        }
    };


    @Override
    public TableRow<ChallengePair> call(TableView<ChallengePair> param)
    {
        final TableRow<ChallengePair> row = new StagingTableRow();

        row.setOnContextMenuRequested(event -> {
            contextRow = row;
            deactivate.setDisable(!row.getItem().isActiveStart()); // && !row.getItem().isActiveFinish());
            contextMenu.show(row.getTableView(), event.getScreenX(), event.getScreenY());
            event.consume();
        });

        row.setOnDragDetected(event -> {
            if (!row.isEmpty()) {
                Integer index = row.getIndex();
                Dragboard db = row.startDragAndDrop(TransferMode.MOVE);
                db.setDragView(row.snapshot(null, null));
                ClipboardContent cc = new ClipboardContent();
                cc.put(SERIALIZED_MIME_TYPE, index);
                db.setContent(cc);
                event.consume();
            }
        });

        row.setOnDragOver(event -> {
            Dragboard db = event.getDragboard();
            if (db.hasContent(SERIALIZED_MIME_TYPE)) {
                if (row.getIndex() != ((Integer)db.getContent(SERIALIZED_MIME_TYPE)).intValue()) {
                    event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
                    event.consume();
                }
            }
        });

        row.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            if (db.hasContent(SERIALIZED_MIME_TYPE)) {
                int draggedIndex = (Integer) db.getContent(SERIALIZED_MIME_TYPE);
                TableView<ChallengePair> table = row.getTableView();
                ChallengePair pair = table.getItems().remove(draggedIndex);
                int dropIndex = row.isEmpty() ? table.getItems().size() : row.getIndex();
                table.getItems().add(dropIndex, pair);
                event.setDropCompleted(true);
                table.getSelectionModel().select(dropIndex);
                event.consume();
            }
        });

        return row;
    }
}
