package org.wwscc.fxchallenge;

import java.util.Collections;

import javafx.beans.property.IntegerProperty;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.util.Callback;

public class StagingRowFactory implements Callback<TableView<ChallengePair>,TableRow<ChallengePair>>
{
    private static final DataFormat SERIALIZED_MIME_TYPE = new DataFormat("application/x-java-serialized-object");

    IntegerProperty highlight;

    public StagingRowFactory(IntegerProperty highlight)
    {
        this.highlight = highlight;
    }

    @Override
    public TableRow<ChallengePair> call(TableView<ChallengePair> param)
    {
        final TableRow<ChallengePair> row = new TableRow<ChallengePair>() {
            @Override
            protected void updateItem(ChallengePair pair, boolean empty) {
                super.updateItem(pair, empty);
                if (pair == null) return;
                if (highlight.get() == pair.round.get()) {
                    if (!getStyleClass().contains("highlightedRow")) {
                        getStyleClass().add("highlightedRow");
                    }
                } else {
                    getStyleClass().removeAll(Collections.singleton("highlightedRow"));
                }
            }
        };

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
