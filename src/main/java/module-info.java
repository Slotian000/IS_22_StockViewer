module com.stockviewer.stockviewer {
    requires jdk.unsupported;
    requires javafx.controls;
    requires javafx.fxml;
    requires org.controlsfx.controls;
    requires org.kordamp.bootstrapfx.core;
    requires java.net.http;
    requires com.google.gson;

    exports com.stockviewer.Controllers;
    exports com.stockviewer;
    exports com.stockviewer.Data;
    exports com.stockviewer.Exceptions;
    opens com.stockviewer to javafx.fxml;
    opens com.stockviewer.Controllers to javafx.fxml;
    exports com.stockviewer.Data.Graphing;
}