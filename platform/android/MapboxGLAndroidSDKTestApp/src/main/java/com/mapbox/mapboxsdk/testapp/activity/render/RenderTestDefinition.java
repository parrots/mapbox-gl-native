package com.mapbox.mapboxsdk.testapp.activity.render;

import com.google.gson.Gson;
import com.mapbox.mapboxsdk.snapshotter.MapSnapshotter;

public class RenderTestDefinition {

  private final static int DEFAULT_WIDTH = 512;
  private final static int DEFAULT_HEIGHT = 512;

  private String category; // eg. background-color
  private String name; // eg. colorSpace-hcl
  private String styleJson;
  private RenderTestStyleDefinition definition;

  RenderTestDefinition(String category, String name, String styleJson) {
    this.category = category;
    this.name = name;
    this.styleJson = styleJson;
    definition = new Gson().fromJson(styleJson, RenderTestStyleDefinition.class);
  }

  public String getName() {
    return name;
  }

  public String getCategory() {
    return category;
  }

  public int getWidth() {
    RenderTestStyleDefinition.Test test = definition.getMetadata().getTest();
    if (test != null) {
      Integer testWidth = test.getWidth();
      if(testWidth!=null && testWidth > 0){
        return testWidth;
      }
    }
    return DEFAULT_WIDTH;
  }

  public int getHeight() {
    RenderTestStyleDefinition.Test test = definition.getMetadata().getTest();
    if (test != null) {
      Integer testHeight = test.getHeight();
      if(testHeight!=null && testHeight > 0){
        return testHeight;
      }
    }
    return DEFAULT_HEIGHT;
  }
  public MapSnapshotter.Options toOptions() {
    return new MapSnapshotter
      .Options(getWidth(), getHeight())
      .withStyleJson(styleJson)
      .withLogo(false);
  }

  @Override
  public String toString() {
    return "RenderTestDefinition{" +
      "category='" + category + '\'' +
      ", name='" + name + '\'' +
      ", styleJson='" + styleJson + '\'' +
      '}';
  }
}
