package com.stockviewer.Controllers;

import com.stockviewer.Data.DataManager;
import com.stockviewer.Data.Order;
import com.stockviewer.StockViewer;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import java.io.IOException;

public class HomePageController {
    @FXML
    private MenuItem copeMenuItem;
    @FXML
    private Menu fileMenu;
    @FXML
    private ImageView graphImageView;
    @FXML
    private Menu helpMenu;
    @FXML
    private MenuItem importMenuItem;
    @FXML
    private MenuBar mainMenuBar;
    @FXML
    private ListView<String> portfolioList;
    @FXML
    private MenuItem resetMenuItem;
    @FXML
    private Button searchButton;
    @FXML
    private TextField searchTextField;
    @FXML
    private MenuItem settingsMenuItem;

    @FXML
    public void initialize() {
        DataManager.getActive().stream().map(Order::toString).forEach(i -> portfolioList.getItems().add(i));
    }

    @FXML
    void SearchAction(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(StockViewer.class.getResource("XML/StockPage.fxml"));
            StockViewer.setSymbol(searchTextField.getText());
            Stage stage = (Stage) searchButton.getScene().getWindow();
            Scene scene = new Scene(loader.load());
            stage.setScene(scene);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}