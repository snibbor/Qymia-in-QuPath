package qupath.extension.qiimia;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class SceneManager {

    private static final Logger logger = LoggerFactory.getLogger(SceneManager.class);
    private final Stage rootStage;

    public SceneManager(Stage rootStage){
        if(rootStage==null){
            throw new IllegalArgumentException();
        }
        this.rootStage = rootStage;
    }

    private final Map<String, Scene> scenes = new HashMap<>();

    public void preloadScene(String sceneUrl, BaseController controller){
        scenes.computeIfAbsent(sceneUrl, u ->{
            FXMLLoader loader = new FXMLLoader(getClass().getResource(u));
            loader.setControllerFactory(controllerClass -> controller);
            try {
                Parent p = loader.load();
                BaseController thisController = loader.getController();
                thisController.setSceneManager(this);
                return new Scene(p);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public Scene getScene(String sceneUrl){
        Scene scene = scenes.get(sceneUrl);
        if(scene == null){
            logger.error("Scene {} does not exist in scenes map!", sceneUrl);
            return null;
        }
        return scene;
    }

//    public BaseController getController(String sceneUrl){
//
//    }

    public void setCSSStyle(String sceneUrl, String styleUrl){
        Scene scene = scenes.get(sceneUrl);
        if(scene == null){
            logger.error("Scene {} does not exist in scenes map!", sceneUrl);
            return;
        }
        scene.getStylesheets().add(getClass().getResource(styleUrl).toExternalForm());
    }

    public void switchScene(String sceneUrl){
        Scene scene = scenes.computeIfAbsent(sceneUrl, u ->{
            FXMLLoader loader = new FXMLLoader(getClass().getResource(u));
            try {
                Parent p = loader.load();
                BaseController controller = loader.getController();
                controller.setSceneManager(this);
                return new Scene(p);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });
        rootStage.setScene(scene);
    }
}
