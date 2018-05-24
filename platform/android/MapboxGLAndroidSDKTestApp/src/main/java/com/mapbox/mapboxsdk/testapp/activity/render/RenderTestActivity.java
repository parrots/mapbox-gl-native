package com.mapbox.mapboxsdk.testapp.activity.render;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import com.mapbox.mapboxsdk.snapshotter.MapSnapshotter;
import okio.BufferedSource;
import okio.Okio;
import timber.log.Timber;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RenderTestActivity extends AppCompatActivity {

  private static final String RENDER_TEST_BASE_PATH = "integration/render-tests";

  private final Map<RenderTestDefinition, Bitmap> renderResultMap = new HashMap<>();
  private final List<MapSnapshotter> mapSnapshotterList = new ArrayList<>();
  private ImageView imageView;
  private OnSnapshotReadyListener onSnapshotReadyListener;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(imageView = new ImageView(RenderTestActivity.this));
    imageView.setLayoutParams(new FrameLayout.LayoutParams(512, 512, Gravity.CENTER));
    try {
      List<RenderTestDefinition> renderTestDefinitions = createRenderTestDefinition();
      renderTests(renderTestDefinitions);
    } catch (IOException exception) {
      Timber.e(exception);
      throw new RuntimeException(exception);
    }
  }

  private List<RenderTestDefinition> createRenderTestDefinition() throws IOException {
    List<RenderTestDefinition> definitions = new ArrayList<>();
    AssetManager assetManager = getAssets();

    String[] categories = assetManager.list(RENDER_TEST_BASE_PATH);
    for (String category : categories) {
      String[] tests = assetManager.list(String.format("%s/%s", RENDER_TEST_BASE_PATH, category));
      for (String test : tests) {
        String styleJson = null;
        try {
          styleJson = loadStyleJson(assetManager, category, test);
        } catch (IOException exception) {
          Timber.e(exception);
        }
        definitions.add(new RenderTestDefinition(category, test, styleJson));

        // TODO REMOVE
        if (definitions.size() == 20) {
          return definitions;
        }
      }
    }

    for (RenderTestDefinition definition : definitions) {
      Timber.e("definitiion %s", definition.toString());
    }

    return definitions;
  }

  private static String loadStyleJson(AssetManager assets, String category, String test) throws IOException {
    InputStream input = assets.open(String.format("%s/%s/%s/style.json", RENDER_TEST_BASE_PATH, category, test));
    BufferedSource source = Okio.buffer(Okio.source(input));
    return source.readByteString().string(Charset.forName("utf-8"));
  }

  private void renderTests(List<RenderTestDefinition> renderTestDefinitions) {
    for (RenderTestDefinition renderTestDefinition : renderTestDefinitions) {
      renderTest(renderTestDefinition, renderTestDefinitions.size());
    }
  }

  private void renderTest(final RenderTestDefinition renderTestDefinition, final int testSize) {
    MapSnapshotter mapSnapshotter = new RenderTestSnapshotter(this, renderTestDefinition.toOptions());
    mapSnapshotterList.add(mapSnapshotter);
    mapSnapshotter.start(result -> {
      Bitmap snapshot = result.getBitmap();
      imageView.setImageBitmap(snapshot);
      renderResultMap.put(renderTestDefinition, snapshot);
      if (renderResultMap.size() == testSize) {
        writeResultsToDisk();
        if (onSnapshotReadyListener != null) {
          onSnapshotReadyListener.onSnapshotReady();
        }
      }
    });
  }

  private void writeResultsToDisk() {
    if (isExternalStorageWritable()) {
      try {
        File testResultDir = createTestResultRootFolder();
        String basePath = testResultDir.getAbsolutePath();

        for (Map.Entry<RenderTestDefinition, Bitmap> testResult : renderResultMap.entrySet()) {
          RenderTestDefinition definition = testResult.getKey();
          String categoryName = definition.getCategory();
          String categoryPath = String.format("%s/%s", basePath, categoryName);
          createCategoryDirectory(categoryPath);
          String testName = testResult.getKey().getName();
          String testDir = createTestDirectory(categoryPath, testName);
          writeTestResultToDisk(testDir, testResult.getValue());
        }
      } catch (final Exception exception) {
        imageView.post(() -> {
          throw new RuntimeException(exception);
        });
      }
    }
  }

  private void createCategoryDirectory(String catPath) {
    File testResultDir = new File(catPath);
    if (testResultDir.exists()) {
      return;
    }

    if (!testResultDir.mkdirs()) {
      throw new RuntimeException("can't create root test directory");
    }
  }

  private File createTestResultRootFolder() {
    File testResultDir = new File(Environment.getExternalStorageDirectory() + File.separator + "mapbox");
    if (testResultDir.exists()) {
      // cleanup old files
      deleteRecursive(testResultDir);
    }

    if (!testResultDir.mkdirs()) {
      throw new RuntimeException("can't create root test directory");
    }
    return testResultDir;
  }

  private void deleteRecursive(File fileOrDirectory) {
    if (fileOrDirectory.isDirectory()) {
      File[] files = fileOrDirectory.listFiles();
      if (files != null) {
        for (File file : files) {
          deleteRecursive(file);
        }
      }
    }

    if (!fileOrDirectory.delete()) {
      throw new RuntimeException("can't delete directory");
    }
  }

  private String createTestDirectory(String basePath, String testName) {
    File testDir = new File(basePath + "/" + testName);
    if (!testDir.exists()) {
      if (!testDir.mkdir()) {
        throw new RuntimeException("can't create sub directory for " + testName);
      }
    }
    return testDir.getAbsolutePath();
  }

  private void writeTestResultToDisk(String testPath, Bitmap testResult) throws IOException {
    String filePath = testPath + "/actual.png";
    FileOutputStream out = new FileOutputStream(filePath);
    testResult.compress(Bitmap.CompressFormat.PNG, 100, out);
    out.flush();
    out.close();
  }

  @Override
  protected void onStop() {
    super.onStop();
    for (MapSnapshotter snapshotter : mapSnapshotterList) {
      snapshotter.cancel();
    }
  }

  private boolean isExternalStorageWritable() {
    return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
  }

  public void setOnSnapshotReadyListener(OnSnapshotReadyListener listener) {
    this.onSnapshotReadyListener = listener;
  }

  public interface OnSnapshotReadyListener {
    void onSnapshotReady();
  }
}
