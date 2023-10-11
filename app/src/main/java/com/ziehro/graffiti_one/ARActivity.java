package com.ziehro.graffiti_one;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentTransaction;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.Toast;

import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ShapeFactory;
import com.google.ar.sceneform.ux.ArFragment;

public class ARActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_CODE = 1234;
    private ArFragment arFragment;
    private boolean isSpraying = false;
    private Vector3 lastPoint = null;
    private boolean isDrawingBlocks = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ar);

        if (!checkARCoreAvailability()) {
            finish();
            return;
        }

        requestCameraPermissionIfNeeded();
        initializeArFragment();

        Button blockButton = findViewById(R.id.blockButton);
        blockButton.setOnClickListener(view -> {
            isDrawingBlocks = !isDrawingBlocks; // Toggle between drawing modes
            if (isDrawingBlocks) {
                blockButton.setBackgroundColor(android.graphics.Color.GRAY); // Change color to indicate active mode
            } else {
                blockButton.setBackgroundColor(android.graphics.Color.WHITE); // Reset color
            }
        });



    }

    @Override
    protected void onResume() {
        super.onResume();
        if (arFragment != null && arFragment.getArSceneView() != null) {
            arFragment.getArSceneView().getScene().setOnTouchListener(this::handleTouch);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            setupTouchListener();
        }
    }
    private void setupTouchListener() {
        arFragment.getArSceneView().getScene().setOnTouchListener(this::handleTouch);
    }


    private boolean checkARCoreAvailability() {
        if (ArCoreApk.getInstance().checkAvailability(this).isSupported()) {
            return true;
        } else {
            showMessage("ARCore is not supported on this device.");
            return false;
        }
    }

    private void requestCameraPermissionIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        }
    }

    private void initializeArFragment() {
        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.arFragmentContainer);
        if (arFragment == null) {
            arFragment = new ArFragment();
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.add(R.id.arFragmentContainer, arFragment);
            ft.commitNow();
        }
    }

    private boolean handleTouch(com.google.ar.sceneform.HitTestResult hitTestResult, MotionEvent motionEvent) {
        switch (motionEvent.getAction()) {
            case MotionEvent.ACTION_DOWN:
                isSpraying = true;
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isSpraying = false;
                lastPoint = null;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (isSpraying) {
                    performSprayAction(motionEvent);
                }
                return true;
            default:
                return false;
        }
    }

    private void performSprayAction(MotionEvent motionEvent) {
        for (HitResult hit : arFragment.getArSceneView().getArFrame().hitTest(motionEvent)) {
            Trackable trackable = hit.getTrackable();
            if (trackable instanceof Plane && ((Plane) trackable).isPoseInPolygon(hit.getHitPose())) {
                if (isDrawingBlocks) {
                    placeBlock(hit.getHitPose());
                } else {
                    Vector3 currentPoint = new Vector3(hit.getHitPose().tx(), hit.getHitPose().ty(), hit.getHitPose().tz());
                    if (lastPoint != null) {
                        drawLineBetweenPoints(lastPoint, currentPoint);
                    }
                    lastPoint = currentPoint;
                }
            }
        }
    }
    private void placeBlock(Pose pose) {
        // Use a similar approach to the placeGraffiti method but place a cube (block) instead.
        MaterialFactory.makeOpaqueWithColor(this, new Color(android.graphics.Color.BLUE))
                .thenAccept(material -> {
                    ModelRenderable renderable = ShapeFactory.makeCube(new Vector3(0.05f, 0.05f, 0.05f), Vector3.zero(), material);
                    Session session = arFragment.getArSceneView().getSession();
                    Anchor anchor = session.createAnchor(pose);

                    AnchorNode anchorNode = new AnchorNode(anchor);
                    anchorNode.setRenderable(renderable);
                    anchorNode.setParent(arFragment.getArSceneView().getScene());
                });
    }


    private void drawLineBetweenPoints(Vector3 start, Vector3 end) {
        // This method will create and place a visual representation of a line between two points.
        // For simplicity, we're using spheres to represent line segments. For a more continuous line, consider using a custom mesh or other methods.
        Vector3 difference = Vector3.subtract(end, start);
        float distance = difference.length();

        // Scale the difference and add to the start point to get the center.
        Vector3 center = Vector3.add(start, scaleVector(difference, 0.5f));



        MaterialFactory.makeOpaqueWithColor(this, new Color(android.graphics.Color.RED))
                .thenAccept(material -> {
                    ModelRenderable renderable = ShapeFactory.makeCylinder(0.02f, distance, center, material);
                    renderable.setShadowCaster(false);
                    renderable.setShadowReceiver(false);

                    AnchorNode anchorNode = new AnchorNode();
                    anchorNode.setRenderable(renderable);
                    anchorNode.setParent(arFragment.getArSceneView().getScene());
                    anchorNode.setWorldPosition(center);
                    Quaternion rotation = rotationBetweenVectors(start, end);
                    anchorNode.setLocalRotation(rotation);

                });
    }

    private void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private Quaternion rotationBetweenVectors(Vector3 start, Vector3 end) {
        Vector3 difference = Vector3.subtract(end, start);
        difference = difference.normalized();

        Vector3 up = new Vector3(0f, 1f, 0f); // Cylinder's default orientation
        Vector3 rotationAxis = Vector3.cross(up, difference);
        float rotationAngle = (float) Math.acos(Vector3.dot(up, difference));

        return new Quaternion(rotationAxis, rotationAngle);
    }


    private Vector3 scaleVector(Vector3 vector, float scalar) {
        return new Vector3(vector.x * scalar, vector.y * scalar, vector.z * scalar);
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_DENIED) {
            showMessage("Camera permission is required for AR.");
        }
    }
}
