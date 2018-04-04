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

public class GraphCurveRenderer {
    private static final int BYTES_PER_FLOAT = Float.SIZE / 8;
    private static final int COORDS_PER_VERTEX = 3;

    private static final String TAG = GraphCurveRenderer.class.getSimpleName();

    // Shader names.
    private static final String VERTEX_SHADER_NAME = "shaders/graph.vert";
    private static final String FRAGMENT_SHADER_NAME = "shaders/graph.frag";

    private final float[] modelMatrix = new float[16];
    private final float[] modelViewMatrix = new float[16];
    private final float[] modelViewProjectionMatrix = new float[16];

    private int vertexCount;
    private int program;

    private float[] min;
    private float[] max;

    private int vertexBufferId;

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

    public void updateCurve(String[] components, String[] bounds) {

        // 3D CURVE

        Argument tArgument = new Argument("t");
        Expression xExpression = new Expression(components[0], tArgument);
        Expression yExpression = new Expression(components[1], tArgument);
        Expression zExpression = new Expression(components[2], tArgument);

        float tMin = Utils.evaluateExpression(bounds[0]);
        float tMax = Utils.evaluateExpression(bounds[1]);
        float tRange = tMax - tMin;

        float increment = tRange / 200;
        int steps = (int) (tRange / increment);

        float[] vertices = new float[steps * 3];

        min = new float[]{Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        max = new float[]{Float.MIN_VALUE, Float.MIN_VALUE, Float.MIN_VALUE};

        float[] coord = new float[3];

        for (int i = 0; i < steps; i++) {
            float t = tMin + i * increment;
            tArgument.setArgumentValue(t);

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

            vertices[i * 3] = y;
            vertices[i * 3 + 1] = z;
            vertices[i * 3 + 2] = x;
        }

        vertexCount = vertices.length / COORDS_PER_VERTEX;

        // -----

        int[] buffers = new int[1];
        GLES20.glGenBuffers(1, buffers, 0);
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

    }

    /**
     * Updates the object model matrix and applies scaling.
     *
     * @param modelMatrix A 4x4 model-to-world transformation matrix, stored in column-major order.
     * @param scaleFactor A separate scaling factor to apply before the {@code modelMatrix}.
     * @see android.opengl.Matrix
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

        //CURVE

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBufferId);
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(
                positionHandle,
                COORDS_PER_VERTEX,
                GLES20.GL_FLOAT,
                false,
                0,
                0
        );

        GLES20.glLineWidth(15);
        GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, vertexCount);

        GLES20.glDisableVertexAttribArray(positionHandle);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        ShaderUtil.checkGLError(TAG, "After draw");
    }

}
