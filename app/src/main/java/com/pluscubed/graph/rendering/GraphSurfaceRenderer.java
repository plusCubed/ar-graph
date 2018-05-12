package com.pluscubed.graph.rendering;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.Matrix;

import com.pluscubed.graph.Utils;
import com.pluscubed.graph.arcore.rendering.ShaderUtil;

import org.mariuszgromada.math.mxparser.Argument;
import org.mariuszgromada.math.mxparser.Expression;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public class GraphSurfaceRenderer {
    public static final int SCALE_FACTOR_INCREMENTS = 1000;

    private static final int BYTES_PER_FLOAT = Float.SIZE / 8;
    private static final int BYTES_PER_SHORT = Short.SIZE / 8;
    private static final int COORDS_PER_VERTEX = 3;

    private static final String TAG = GraphSurfaceRenderer.class.getSimpleName();

    // Shader names.
    private static final String VERTEX_SHADER_NAME = "shaders/graph.vert";
    private static final String FRAGMENT_SHADER_NAME = "shaders/graph.frag";

    private final float[] modelMatrix = new float[16];
    private final float[] modelViewMatrix = new float[16];
    private final float[] modelViewProjectionMatrix = new float[16];

    private int program;

    private float[] min;
    private float[] max;

    private int vertexBufferId;
    private int indexBufferId;

    private int indicesCount;

    private int positionHandle;
    private int mvpMatrixHandle;
    private int minHandle;
    private int maxHandle;

    public void createOnGlThread(Context context) throws IOException {
        final int vertexShader =
                ShaderUtil.loadGLShader(TAG, context, GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_NAME);
        final int fragmentShader =
                ShaderUtil.loadGLShader(TAG, context, GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_NAME);

        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);

        positionHandle = GLES20.glGetAttribLocation(program, "a_Position");
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "u_ModelViewProjection");
        minHandle = GLES20.glGetUniformLocation(program, "u_Min");
        maxHandle = GLES20.glGetUniformLocation(program, "u_Max");

        Matrix.setIdentityM(modelMatrix, 0);
    }

    public void updateSurface(String[] components, String[] tBounds, String[] uBounds, float scaleFactor) {
        // 3D SURFACE

        Argument tArgument = new Argument("t");
        Argument uArgument = new Argument("u");
        Expression xExpression = new Expression(components[0], tArgument, uArgument);
        Expression yExpression = new Expression(components[1], tArgument, uArgument);
        Expression zExpression = new Expression(components[2], tArgument, uArgument);

        float tMin = Utils.evaluateExpression(tBounds[0]);
        float tMax = Utils.evaluateExpression(tBounds[1]);
        float tRange = tMax - tMin;

        float uMin = Utils.evaluateExpression(uBounds[0]);
        float uMax = Utils.evaluateExpression(uBounds[1]);
        float uRange = uMax - uMin;

        float increments = scaleFactor * SCALE_FACTOR_INCREMENTS;

        // Add one
        int tSteps = (int) increments + 1;
        int uSteps = (int) increments + 1;

        // Ordered t, then u
        float[] vertices = new float[tSteps * uSteps * 3];

        min = new float[]{Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        max = new float[]{Float.MIN_VALUE, Float.MIN_VALUE, Float.MIN_VALUE};

        float[] coord = new float[3];

        for (int ui = 0; ui < uSteps; ui++) {
            for (int ti = 0; ti < tSteps; ti++) {
                float t = tMin + (float) ti / (tSteps - 1) * tRange;
                float u = uMin + (float) ui / (uSteps - 1) * uRange;

                tArgument.setArgumentValue(t);
                uArgument.setArgumentValue(u);

                float x = (float) (xExpression.calculate());
                float y = (float) (yExpression.calculate());
                float z = (float) (zExpression.calculate());

                coord[0] = y;
                coord[1] = z;
                coord[2] = x;
                for (int j = 0; j < 3; j++) {
                    if (coord[j] < min[j])
                        min[j] = coord[j];
                    if (coord[j] > max[j])
                        max[j] = coord[j];
                }

                vertices[(ui * tSteps + ti) * 3] = y;
                vertices[(ui * tSteps + ti) * 3 + 1] = z;
                vertices[(ui * tSteps + ti) * 3 + 2] = x;
            }
        }

        int[] buffers = new int[2];
        GLES20.glGenBuffers(2, buffers, 0);

        // -----

        vertexBufferId = buffers[0];
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBufferId);

        ByteBuffer bb = ByteBuffer.allocateDirect(vertices.length * BYTES_PER_FLOAT);
        bb.order(ByteOrder.nativeOrder());
        FloatBuffer vertexBuffer = bb.asFloatBuffer();
        vertexBuffer
                .put(vertices)
                .position(0);

        GLES20.glBufferData(
                GLES20.GL_ARRAY_BUFFER,
                vertices.length * BYTES_PER_FLOAT,
                vertexBuffer,
                GLES20.GL_STATIC_DRAW
        );

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        //Index

        indexBufferId = buffers[1];
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBufferId);

        //2 indices per segment * number of segments * lines
        short[] indices = new short[2 * (tSteps - 1) * uSteps + 2 * (uSteps - 1) * tSteps];

        int i = 0;

        // Horizontal grid lines (y constant)
        for (int ui = 0; ui < uSteps; ui++) {
            for (int t = 0; t < tSteps - 1; t++) {
                //start vertex index
                indices[i++] = (short) (ui * tSteps + t);
                //end vertex index
                indices[i++] = (short) (ui * tSteps + t + 1);
            }
        }

        // Vertical grid lines (x constant)
        for (int ti = 0; ti < tSteps; ti++) {
            for (int ui = 0; ui < uSteps - 1; ui++) {
                indices[i++] = (short) (ui * tSteps + ti);
                indices[i++] = (short) ((ui + 1) * tSteps + ti);
            }
        }

        indicesCount = indices.length;

        bb = ByteBuffer.allocateDirect(indices.length * BYTES_PER_SHORT);
        bb.order(ByteOrder.nativeOrder());
        ShortBuffer indexBuffer = bb.asShortBuffer();
        indexBuffer
                .put(indices)
                .position(0);

        GLES20.glBufferData(
                GLES20.GL_ELEMENT_ARRAY_BUFFER,
                indices.length * BYTES_PER_SHORT,
                indexBuffer,
                GLES20.GL_STATIC_DRAW
        );

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);

        ShaderUtil.checkGLError(TAG, "after update");

    }

    /**
     * Updates the object model matrix and applies scaling.
     *
     * @param modelMatrix A 4x4 model-to-world transformation matrix, stored in column-major order.
     * @param scaleFactor A separate scaling factor to apply before the {@code modelMatrix}.
     * @see Matrix
     */
    public void updateModelMatrix(float[] modelMatrix, float scaleFactor) {
        float[] scaleMatrix = new float[16];
        Matrix.setIdentityM(scaleMatrix, 0);
        scaleMatrix[0] = scaleFactor;
        scaleMatrix[5] = scaleFactor;
        scaleMatrix[10] = scaleFactor;
        Matrix.multiplyMM(this.modelMatrix, 0, modelMatrix, 0, scaleMatrix, 0);
    }

    public void draw(float[] viewmtx, float[] projmtx, float[] colorCorrectionRgba) {
        ShaderUtil.checkGLError(TAG, "Before draw");

        // Build the ModelView and ModelViewProjection matrices
        // for calculating object position and light.
        Matrix.multiplyMM(modelViewMatrix, 0, viewmtx, 0, modelMatrix, 0);
        Matrix.multiplyMM(modelViewProjectionMatrix, 0, projmtx, 0, modelViewMatrix, 0);

        GLES20.glUseProgram(program);

        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, modelViewProjectionMatrix, 0);
        GLES20.glUniform3fv(minHandle, 1, min, 0);
        GLES20.glUniform3fv(maxHandle, 1, max, 0);

        //SURFACE

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBufferId);
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(
                positionHandle,
                3,
                GLES20.GL_FLOAT,
                false,
                0,
                0
        );

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBufferId);

        GLES20.glLineWidth(15);
        GLES20.glDrawElements(GLES20.GL_LINES, indicesCount, GLES20.GL_UNSIGNED_SHORT, 0);

        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);

        ShaderUtil.checkGLError(TAG, "After draw");
    }

}
