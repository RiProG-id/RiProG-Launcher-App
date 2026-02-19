package com.riprog.launcher.data.local.db.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import com.riprog.launcher.data.model.HomeItem;

@Entity(tableName = "home_items")
public class HomeItemEntity {
    @PrimaryKey(autoGenerate = true)
    public long id = 0;
    public Long parentId = null;
    public String type;
    public String packageName = null;
    public String className = null;
    public String folderName = null;
    public float row;
    public float col;
    public int spanX;
    public int spanY;
    public int page;
    public int widgetId;
    public float rotation;
    public float scale;
    public float tiltX;
    public float tiltY;

    public HomeItem toDomainModel() {
        HomeItem item = new HomeItem();
        item.setType(HomeItem.Type.valueOf(type));
        item.setPackageName(packageName);
        item.setClassName(className);
        item.setFolderName(folderName);
        item.setRow(row);
        item.setCol(col);
        item.setSpanX(spanX);
        item.setSpanY(spanY);
        item.setPage(page);
        item.setWidgetId(widgetId);
        item.setRotation(rotation);
        item.setScale(scale);
        item.setTiltX(tiltX);
        item.setTiltY(tiltY);
        return item;
    }

    public static HomeItemEntity fromDomainModel(HomeItem item, Long parentId) {
        HomeItemEntity entity = new HomeItemEntity();
        entity.parentId = parentId;
        entity.type = item.getType() != null ? item.getType().name() : HomeItem.Type.APP.name();
        entity.packageName = item.getPackageName();
        entity.className = item.getClassName();
        entity.folderName = item.getFolderName();
        entity.row = item.getRow();
        entity.col = item.getCol();
        entity.spanX = item.getSpanX();
        entity.spanY = item.getSpanY();
        entity.page = item.getPage();
        entity.widgetId = item.getWidgetId();
        entity.rotation = item.getRotation();
        entity.scale = item.getScale();
        entity.tiltX = item.getTiltX();
        entity.tiltY = item.getTiltY();
        return entity;
    }
}
