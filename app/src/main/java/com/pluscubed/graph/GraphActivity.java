package com.pluscubed.graph;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.Point.OrientationMode;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
import com.pluscubed.graph.arcore.helpers.CameraPermissionHelper;
import com.pluscubed.graph.arcore.helpers.DisplayRotationHelper;
import com.pluscubed.graph.arcore.helpers.FullScreenHelper;
import com.pluscubed.graph.arcore.helpers.SnackbarHelper;
import com.pluscubed.graph.arcore.helpers.TapHelper;
import com.pluscubed.graph.arcore.rendering.BackgroundRenderer;
import com.pluscubed.graph.arcore.rendering.PlaneRenderer;
import com.pluscubed.graph.arcore.rendering.PointCloudRenderer;
import com.pluscubed.graph.rendering.AxesRenderer;
import com.pluscubed.graph.rendering.GraphCurveRenderer;
import com.pluscubed.graph.rendering.GraphFunctionRenderer;
import com.pluscubed.graph.rendering.GraphSurfaceRenderer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import butterknife.BindView;
import butterknife.BindViews;
import butterknife.ButterKnife;

public class GraphActivity extends AppCompatActivity implements GLSurfaceView.Renderer {
    private static final String TAG = GraphActivity.class.getSimpleName();

    public static final float INITIAL_SCALE_FACTOR = 0.05f;

    private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
    private final PointCloudRenderer pointCloudRenderer = new PointCloudRenderer();
    // Anchors created from taps used for object placing.
    private final ArrayList<Anchor> anchors = new ArrayList<>();
    // Rendering. The Renderers are created here, and initialized when the GL surface is created.
    @BindView(R.id.surfaceview)
    GLSurfaceView surfaceView;
    private boolean installRequested;
    private Session session;

    private final BackgroundRenderer backgroundRenderer = new BackgroundRenderer();
    private final PlaneRenderer planeRenderer = new PlaneRenderer();
    private DisplayRotationHelper displayRotationHelper;

    private final GraphSurfaceRenderer surfaceObject = new GraphSurfaceRenderer();
    private final GraphCurveRenderer curveObject = new GraphCurveRenderer();
    private final AxesRenderer axesRenderer = new AxesRenderer();
    private final GraphFunctionRenderer functionObject = new GraphFunctionRenderer();

    // Temporary matrix allocated here to reduce number of allocations for each frame.
    private final float[] anchorMatrix = new float[16];
    private TapHelper tapHelper;

    @BindViews({R.id.para1, R.id.para2, R.id.para3})
    List<EditText> parametricEditTexts;
    @BindView(R.id.view_parametric)
    Button viewParametricButton;
    @BindView(R.id.hide_parametric)
    Button hideParametricButton;
    @BindView(R.id.tbounds)
    BoundsView tBoundsView;
    @BindView(R.id.ubounds)
    BoundsView uBoundsView;

    @BindView(R.id.function)
    EditText functionEditText;
    @BindView(R.id.view_function)
    Button viewFunctionButton;
    @BindView(R.id.hide_function)
    Button hideFunctionButton;
    @BindView(R.id.xbounds)
    BoundsView xBoundsView;
    @BindView(R.id.ybounds)
    BoundsView yBoundsView;

    private String[] parametricComponents = new String[3];
    private String[] tBounds = new String[2];
    private String[] uBounds = new String[2];
    private boolean updateParametricGraph;
    private boolean parametricVisible;
    private boolean isParametricSurface;

    private String zFunction;
    private String[] xBounds = new String[2];
    private String[] yBounds = new String[2];
    private boolean updateFunctionGraph;
    private boolean functionVisible;

    private float scaleFactor = INITIAL_SCALE_FACTOR;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        viewParametricButton.setOnClickListener(view -> {
            parametricVisible = true;
            queueUpdateParametric();
        });
        hideParametricButton.setOnClickListener(view -> {
            parametricVisible = false;
        });
        tBoundsView.setBounds(new String[]{"0", "2*pi"});
        uBoundsView.setBounds(new String[]{"0", "2*pi"});

        viewFunctionButton.setOnClickListener(v -> {
            functionVisible = true;
            queueUpdateFunction();
        });
        hideFunctionButton.setOnClickListener(view -> {
            functionVisible = false;
        });
        xBoundsView.setBounds(new String[]{"-5", "5"});
        yBoundsView.setBounds(new String[]{"-5", "5"});

        displayRotationHelper = new DisplayRotationHelper(this);

        // Set up tap listener.
        tapHelper = new TapHelper(this);
        surfaceView.setOnTouchListener(tapHelper);

        // Set up renderer.
        surfaceView.setPreserveEGLContextOnPause(true);
        surfaceView.setEGLContextClientVersion(2);
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
        surfaceView.setRenderer(this);
        surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        installRequested = false;
    }

    private void queueUpdateParametric() {
        parametricComponents[0] = parametricEditTexts.get(0).getText().toString();
        parametricComponents[1] = parametricEditTexts.get(1).getText().toString();
        parametricComponents[2] = parametricEditTexts.get(2).getText().toString();
        tBounds = tBoundsView.getBounds();
        uBounds = uBoundsView.getBounds();
        updateParametricGraph = true;
        isParametricSurface = parametricComponents[0].contains("u") || parametricComponents[1].contains("u") || parametricComponents[2].contains("u");
    }

    private void queueUpdateFunction() {
        zFunction = functionEditText.getText().toString();
        xBounds = xBoundsView.getBounds();
        yBounds = yBoundsView.getBounds();
        updateFunctionGraph = true;
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (session == null) {
            Exception exception = null;
            String message = null;
            try {
                switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    case INSTALL_REQUESTED:
                        installRequested = true;
                        return;
                    case INSTALLED:
                        break;
                }

                // ARCore requires camera permissions to operate. If we did not yet obtain runtime
                // permission on Android M and above, now is a good time to ask the user for it.
                if (!CameraPermissionHelper.hasCameraPermission(this)) {
                    CameraPermissionHelper.requestCameraPermission(this);
                    return;
                }

                // Create the session.
                session = new Session(/* context= */ this);

            } catch (UnavailableArcoreNotInstalledException
                    | UnavailableUserDeclinedInstallationException e) {
                message = "Please install ARCore";
                exception = e;
            } catch (UnavailableApkTooOldException e) {
                message = "Please update ARCore";
                exception = e;
            } catch (UnavailableSdkTooOldException e) {
                message = "Please update this app";
                exception = e;
            } catch (UnavailableDeviceNotCompatibleException e) {
                message = "This device does not support AR";
                exception = e;
            } catch (Exception e) {
                message = "Failed to create AR session";
                exception = e;
            }

            if (message != null) {
                messageSnackbarHelper.showError(this, message);
                Log.e(TAG, "Exception creating session", exception);
                return;
            }
        }

        // Note that order matters - see the note in onPause(), the reverse applies here.
        try {
            session.resume();
        } catch (CameraNotAvailableException e) {
            // In some cases (such as another camera app launching) the camera may be given to
            // a different app instead. Handle this properly by showing a message and recreate the
            // session at the next iteration.
            messageSnackbarHelper.showError(this, "Camera not available. Please restart the app.");
            session = null;
            return;
        }

        surfaceView.onResume();
        displayRotationHelper.onResume();

        messageSnackbarHelper.showMessage(this, "Searching for surfaces...");
    }

    @Override
    public void onPause() {
        super.onPause();
        if (session != null) {
            // Note that the order matters - GLSurfaceView is paused first so that it does not try
            // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
            // still call session.update() and get a SessionPausedException.
            displayRotationHelper.onPause();
            surfaceView.onPause();
            session.pause();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
                    .show();
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                CameraPermissionHelper.launchPermissionSettings(this);
            }
            finish();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

        // Prepare the rendering objects. This involves reading shaders, so may throw an IOException.
        try {
            // Create the texture and pass it to ARCore session to be filled during update().
            backgroundRenderer.createOnGlThread(this);
            planeRenderer.createOnGlThread(this, "arcore/models/trigrid.png");
            pointCloudRenderer.createOnGlThread(this);

            curveObject.createOnGlThread(this);
            functionObject.createOnGlThread(this);
            surfaceObject.createOnGlThread(this);

            axesRenderer.createOnGlThread(this);

        } catch (IOException e) {
            Log.e(TAG, "Failed to read an asset file", e);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        displayRotationHelper.onSurfaceChanged(width, height);
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // Clear screen to notify driver it should not load any pixels from previous frame.
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        if (session == null) {
            return;
        }
        // Notify ARCore session that the view size changed so that the perspective matrix and
        // the video background can be properly adjusted.
        displayRotationHelper.updateSessionIfNeeded(session);

        try {
            session.setCameraTextureName(backgroundRenderer.getTextureId());

            // Obtain the current frame from ARSession. When the configuration is set to
            // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
            // camera framerate.
            Frame frame = session.update();
            Camera camera = frame.getCamera();

            // Handle taps. Handling only one tap per frame, as taps are usually low frequency
            // compared to frame rate.
            MotionEvent tap = tapHelper.poll();
            if (tap != null && camera.getTrackingState() == TrackingState.TRACKING) {
                for (HitResult hit : frame.hitTest(tap)) {
                    // Check if any plane was hit, and if it was hit inside the plane polygon
                    Trackable trackable = hit.getTrackable();
                    // Creates an anchor if a plane or an oriented point was hit.
                    if ((trackable instanceof Plane
                            && ((Plane) trackable).isPoseInPolygon(hit.getHitPose())
                            && (PlaneRenderer.calculateDistanceToPlane(hit.getHitPose(), camera.getPose())
                            > 0))
                            || (trackable instanceof Point
                            && ((Point) trackable).getOrientationMode()
                            == OrientationMode.ESTIMATED_SURFACE_NORMAL)) {
                        // Hits are sorted by depth. Consider only closest hit on a plane or oriented point.
                        // Cap the number of objects created. This avoids overloading both the
                        // rendering system and ARCore.
                        if (anchors.size() >= 1) {
                            anchors.get(0).detach();
                            anchors.remove(0);
                        }
                        // Adding an Anchor tells ARCore that it should track this position in
                        // space. This anchor is created on the Plane to place the 3D model
                        // in the correct position relative both to the world and to the plane.
                        anchors.add(hit.createAnchor());
                        break;
                    }
                }
            }

            // Draw background.
            backgroundRenderer.draw(frame);

            // If not tracking, don't draw 3d objects.
            if (camera.getTrackingState() == TrackingState.PAUSED) {
                return;
            }

            // Get projection matrix.
            float[] projmtx = new float[16];
            camera.getProjectionMatrix(projmtx, 0, 0.1f, 100.0f);

            // Get camera matrix and draw.
            float[] viewmtx = new float[16];
            camera.getViewMatrix(viewmtx, 0);

            // Compute lighting from average intensity of the image.
            // The first three components are color scaling factors.
            // The last one is the average pixel intensity in gamma space.
            final float[] colorCorrectionRgba = new float[4];
            frame.getLightEstimate().getColorCorrection(colorCorrectionRgba, 0);

            // Visualize tracked points.
            PointCloud pointCloud = frame.acquirePointCloud();
            pointCloudRenderer.update(pointCloud);
            pointCloudRenderer.draw(viewmtx, projmtx);

            // Application is responsible for releasing the point cloud resources after
            // using it.
            pointCloud.release();

            // Check if we detected at least one plane. If so, hide the loading message.
            if (messageSnackbarHelper.isShowing()) {
                for (Plane plane : session.getAllTrackables(Plane.class)) {
                    if (plane.getType() == com.google.ar.core.Plane.Type.HORIZONTAL_UPWARD_FACING
                            && plane.getTrackingState() == TrackingState.TRACKING) {
                        messageSnackbarHelper.hide(this);
                        break;
                    }
                }
            }

            if (anchors.size() == 0) {
                // Visualize planes.
                planeRenderer.drawPlanes(
                        session.getAllTrackables(Plane.class), camera.getDisplayOrientedPose(), projmtx);
            }

            // Visualize anchors created by touch.
            for (Anchor anchor : anchors) {
                if (anchor.getTrackingState() != TrackingState.TRACKING) {
                    continue;
                }
                // Get the current pose of an Anchor in world space. The Anchor pose is updated
                // during calls to session.update() as ARCore refines its estimate of the world.
                anchor.getPose().toMatrix(anchorMatrix, 0);

                scaleFactor *= tapHelper.fetchScaleFactor();

                axesRenderer.updateModelMatrix(anchorMatrix, 0.5f);
                axesRenderer.draw(viewmtx, projmtx, colorCorrectionRgba);

                boolean scaleEnded = tapHelper.fetchScaleEnded();
                if (parametricVisible) {
                    if (isParametricSurface) {
                        if (updateParametricGraph || scaleEnded) {
                            surfaceObject.updateSurface(parametricComponents, tBounds, uBounds, scaleFactor);
                        }
                        surfaceObject.updateModelMatrix(anchorMatrix, scaleFactor);
                        surfaceObject.draw(viewmtx, projmtx, colorCorrectionRgba);
                    } else {
                        if (updateParametricGraph || scaleEnded) {
                            curveObject.updateCurve(parametricComponents, tBounds, scaleFactor);
                        }
                        curveObject.updateModelMatrix(anchorMatrix, scaleFactor);
                        curveObject.draw(viewmtx, projmtx, colorCorrectionRgba);
                    }
                }

                if (functionVisible) {
                    if (updateFunctionGraph || scaleEnded) {
                        functionObject.updateSurface(zFunction, xBounds, yBounds, scaleFactor);
                    }
                    functionObject.updateModelMatrix(anchorMatrix, scaleFactor);
                    functionObject.draw(viewmtx, projmtx, colorCorrectionRgba);
                }

                updateParametricGraph = false;
                updateFunctionGraph = false;
            }

        } catch (Throwable t) {
            // Avoid crashing the application due to unhandled exceptions.
            Log.e(TAG, "Exception on the OpenGL thread", t);
        }
    }
}
