package client;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

public class HelloController {

    Label greetingLabel;
    TextField nameField;
    Button greetButton;

    public void initialize() {
    }

    public void greet(javafx.event.ActionEvent event) {
        greetingLabel.setText(nameField.getText());
    }
}
