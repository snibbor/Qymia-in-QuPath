package qupath.extension.qiima.operations;

import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Collectors;

import javafx.scene.control.TreeItem;

public class OperationTreeItem<C, D> extends TreeItem<D>{
	public OperationTreeItem(C container, Function<C, D> dataFunction, Function<C, Collection<? extends C>> childFunction) {
		super(dataFunction.apply(container));
        getChildren().addAll(childFunction.apply(container)
                .stream()
                .map(childContainer -> new OperationTreeItem<C, D>(childContainer, dataFunction, childFunction))
                .collect(Collectors.toList()));
	}
}

//Example use
//https://stackoverflow.com/questions/45607464/is-it-possible-to-generate-a-javafx-treeitems-children-dynamically-based-on-a-f

//Function<MyData, MyData> dataFunction = c -> c;
//Function<MyData, Collection<? extends MyData>> childFunction = c -> c.getChildren();
//
//treeTableView.setRoot(new AutomatedTreeItem<MyData, MyData>(myRootData, dataFunction, childFunction));