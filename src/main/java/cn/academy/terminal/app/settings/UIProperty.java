package cn.academy.terminal.app.settings;

import net.minecraft.util.StatCollector;

public class UIProperty {
	
	public final IPropertyElement element;
	public final String category;
	public final String id;
	public final Object defValue;
	
	public UIProperty(IPropertyElement _element, String _category, String _id, Object _defValue) {
		element = _element;
		category = _category;
		id = _id;
		defValue = _defValue;
	}
	
	public String getDisplayID() {
		return StatCollector.translateToLocal("ac.settings.prop." + id);
	}
	
}
