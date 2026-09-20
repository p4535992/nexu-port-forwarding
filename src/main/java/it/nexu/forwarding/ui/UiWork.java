package it.nexu.forwarding.ui;

import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import java.util.concurrent.Callable;

/** Slow password derivations and file I/O never execute on the JavaFX thread. */
public final class UiWork {
    private static int depth;
    public static boolean busy() { return depth > 0; }
    private UiWork() { }
    public static <T> T run(Window owner,String title,Callable<T> work) throws Exception {
        Task<T> task=new Task<>() { @Override protected T call() throws Exception { return work.call(); } };
        Dialog<Void> dialog=new Dialog<>(); dialog.initOwner(owner); dialog.setTitle(title);
        dialog.getDialogPane().setContent(new VBox(14,new Label(title),new ProgressIndicator()));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(true);
        dialog.setOnCloseRequest(e->{ if(!task.isDone()) e.consume(); });
        task.setOnSucceeded(e->dialog.close()); task.setOnFailed(e->dialog.close());
        dialog.setOnShown(e->Thread.ofVirtual().start(task)); depth++; try { dialog.showAndWait(); } finally { depth--; }
        if(task.getException()!=null) throw new Exception(task.getException().getMessage(),task.getException());
        return task.getValue();
    }
}
