package hu.hexadecimal.quantum.graphics;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.IntStream;

import androidx.core.graphics.PaintCompat;
import hu.hexadecimal.quantum.GateSequence;
import hu.hexadecimal.quantum.VisualOperator;

public class QuantumView extends View {

    final Paint mPaint, otherPaint, mTextPaint, whiteTextPaint;
    final int mPadding;
    final Path mPath;

    private LinkedList<VisualOperator> gos;
    private short[] measuredQubits;
    public boolean saved;

    public volatile boolean shouldStop;

    /**
     * VIsual QUantum-gate Sequence
     */
    public static final String FILE_EXTENSION_LEGACY = ".viqus";
    /**
     * Quantum Sequence File
     */
    public static final String FILE_EXTENSION = ".qsf";
    public static final String OPENQASM_FILE_EXTENSION = ".qasm";

    public static final int STEP = 70;
    public static final int MAX_QUBITS = 10;
    public static final int GATE_SIZE = 18;
    public final float UNIT;
    public final float START_Y;
    public final float START_X;

    public String name = "";

    public QuantumView(Context context) {
        super(context);
        UNIT = pxFromDp(super.getContext(), 1);
        START_Y = pxFromDp(super.getContext(), 20);
        START_X = pxFromDp(super.getContext(), 40);
        gos = new LinkedList<>();

        mPath = new Path();
        mPath.setFillType(Path.FillType.EVEN_ODD);

        mPaint = new Paint();
        mPaint.setAntiAlias(true);

        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setColor(Color.BLUE);
        mPaint.setStrokeWidth(pxFromDp(context, 2.5f));

        measuredQubits = new short[MAX_QUBITS];

        mTextPaint = new Paint(Paint.LINEAR_TEXT_FLAG | Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setColor(Color.DKGRAY);
        mTextPaint.setTextSize(pxFromDp(context, 24));

        whiteTextPaint = new Paint(Paint.LINEAR_TEXT_FLAG | Paint.ANTI_ALIAS_FLAG);
        whiteTextPaint.setColor(0xffffffff);
        whiteTextPaint.setTextSize(pxFromDp(context, 24));
        whiteTextPaint.setTypeface(Typeface.MONOSPACE);

        otherPaint = new Paint();

        mPadding = (int) pxFromDp(context, 32);
        saved = true;
    }

    public double getLimit() {
        return getHeight() - 1.4 * mPadding - START_Y;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        mPaint.setColor(0xff888888);

        otherPaint.setStyle(Paint.Style.FILL);
        int qubitPos = 0;
        char verticalBar = PaintCompat.hasGlyph(whiteTextPaint, "⎥") ? '⎥' : '|';
        for (int i = (int) START_Y; i < getLimit() && i <= pxFromDp(super.getContext(), STEP * MAX_QUBITS); i += (int) pxFromDp(super.getContext(), STEP)) {
            canvas.drawLine(START_X, mPadding + i, getWidth() - mPadding, mPadding + i, mPaint);

            mPath.reset();
            mPath.moveTo(getWidth() - mPadding - pxFromDp(super.getContext(), 5), mPadding + i - pxFromDp(super.getContext(), 5));
            mPath.lineTo(getWidth() - mPadding + UNIT / 2, mPadding + i);
            mPath.lineTo(getWidth() - mPadding - pxFromDp(super.getContext(), 5), mPadding + i + pxFromDp(super.getContext(), 5));
            mPath.close();
            canvas.drawPath(mPath, mPaint);

            otherPaint.setColor(measuredQubits[qubitPos] > 0 ? 0xffBA2121 : 0xff555555);
            canvas.drawRect(START_X,
                    mPadding + i - pxFromDp(super.getContext(), GATE_SIZE),
                    START_X + pxFromDp(super.getContext(), GATE_SIZE * 2),
                    mPadding + i + pxFromDp(super.getContext(), GATE_SIZE),
                    otherPaint);
            String qText = "q" + Math.round((i + START_Y) / pxFromDp(super.getContext(), STEP));
            canvas.drawText(qText, (START_X - mTextPaint.measureText(qText)) / 2, mPadding + i + pxFromDp(super.getContext(), 6), mTextPaint);
            canvas.drawText(verticalBar + "0⟩", START_X + (verticalBar == '|' ? -pxFromDp(super.getContext(), 2.8f) : pxFromDp(super.getContext(), 2f)), mPadding + i + pxFromDp(super.getContext(), 8f), whiteTextPaint);
            qubitPos++;
        }
        int[] gatesNumber = new int[MAX_QUBITS];
        RectF bounds = new RectF();
        for (int i = 0; i < gos.size(); i++) {
            VisualOperator v = gos.get(i);
            v.resetRect();
            final int[] qubitIDs = v.getQubitIDs();
            boolean controlled = false;
            for (int j = 0; j < qubitIDs.length; j++) {
                gatesNumber[qubitIDs[j]]++;
                otherPaint.setColor(v.getColor());
                bounds.left = (START_X + pxFromDp(super.getContext(), 2) + gatesNumber[qubitIDs[j]] * pxFromDp(super.getContext(), GATE_SIZE * 3));
                bounds.top = (mPadding + pxFromDp(super.getContext(), 2) + (pxFromDp(super.getContext(), STEP) * (qubitIDs[j])));
                RectF areaRect = new RectF(bounds.left,
                        bounds.top,
                        bounds.left + pxFromDp(super.getContext(), GATE_SIZE * 2),
                        bounds.top + pxFromDp(super.getContext(), GATE_SIZE * 2));
                String symbol = v.getSymbols()[j];
                switch (symbol.length()) {
                    case 1:
                    case 2:
                        whiteTextPaint.setTextSize(pxFromDp(super.getContext(), 24));
                        break;
                    case 3:
                        whiteTextPaint.setTextSize(pxFromDp(super.getContext(), 18));
                        break;
                    case 4:
                        whiteTextPaint.setTextSize(pxFromDp(super.getContext(), 14));
                        break;
                    default:
                        whiteTextPaint.setTextSize(pxFromDp(super.getContext(), 12));
                }
                v.addRect(areaRect);
                bounds.right = whiteTextPaint.measureText(symbol, 0, symbol.length());
                bounds.bottom = whiteTextPaint.descent() - whiteTextPaint.ascent();
                bounds.left += (areaRect.width() - bounds.right) / 2.0f;
                bounds.top += (areaRect.height() - bounds.bottom) / 2.0f;

                canvas.drawRect(areaRect, otherPaint);
                if (j != 0) {
                    mPaint.setColor(v.getColor());
                    float center1x = areaRect.centerX();
                    float center1y = areaRect.centerY();
                    RectF a = v.getRect().get(j - 1);
                    float center2x = a.centerX();
                    float center2y = a.centerY();
                    center2x += (pxFromDp(super.getContext(), GATE_SIZE / 1.1f) * (center1x - center2x) / Math.sqrt(Math.pow((center2x - center1x), 2) + Math.pow((center2y - center1y), 2)));
                    center2y += pxFromDp(super.getContext(), GATE_SIZE / 1.1f) * (center1y - center2y) / Math.sqrt(Math.pow((center2x - center1x), 2) + Math.pow((center2y - center1y), 2));
                    canvas.drawLine(center1x, center1y, center2x, center2y, mPaint);
                }

                canvas.drawText(symbol, bounds.left, bounds.top - whiteTextPaint.ascent(), whiteTextPaint);

                if (symbol.equals("●")) {
                    controlled = true;
                    whiteTextPaint.setTextSize(pxFromDp(super.getContext(), 11));
                    canvas.drawText("C", areaRect.right - (whiteTextPaint.measureText("C") * 1.3f), bounds.top - (whiteTextPaint.ascent() / 1.3f), whiteTextPaint);
                } else if (controlled) {
                    whiteTextPaint.setTextSize(pxFromDp(super.getContext(), 11));
                    canvas.drawText("T", areaRect.right - (whiteTextPaint.measureText("T") * 1.3f), bounds.top - (whiteTextPaint.ascent() / 1.3f), whiteTextPaint);
                }
            }
        }
        whiteTextPaint.setTextSize(pxFromDp(super.getContext(), 24));

    }

    public VisualOperator whichGate(float posx, float posy) {
        for (int i = 0; i < gos.size(); i++) {
            List<RectF> rectList = gos.get(i).getRect();
            for (int j = 0; j < rectList.size(); j++) {
                if (rectList.get(j).contains(posx, posy)) {
                    return gos.get(i);
                }
            }
        }
        return null;
    }

    public boolean replaceGateAt(int[] qubits, VisualOperator visualOperator, float posx, float posy) {
        if (posx < 0 || posy < 0) {
            addGate(qubits, visualOperator);
            return true;
        } else {
            for (int i = 0; i < gos.size(); i++) {
                List<RectF> rectList = gos.get(i).getRect();
                for (int j = 0; j < rectList.size(); j++) {
                    if (rectList.get(j).contains(posx, posy)) {
                        for (int qubit : gos.get(i).getQubitIDs()) {
                            measuredQubits[qubit]--;
                        }
                        gos.remove(i);
                        for (int qubit : qubits) {
                            if (qubit >= getDisplayedQubits()) {
                                invalidate();
                                return false;
                            }
                            if (!canAddGate(qubit))
                                setLayoutParams(new LinearLayout.LayoutParams(getWidth() + 400, ViewGroup.LayoutParams.MATCH_PARENT));
                            measuredQubits[qubit]++;
                        }
                        visualOperator.setQubitIDs(qubits);
                        gos.add(i, visualOperator);
                        invalidate();
                        saved = false;
                        return true;
                    }
                }
            }
            gos.addLast(visualOperator);
            invalidate();
            return false;
        }
    }

    public boolean deleteGateAt(float posx, float posy) {
        for (int i = 0; i < gos.size(); i++) {
            List<RectF> rectList = gos.get(i).getRect();
            for (int j = 0; j < rectList.size(); j++) {
                if (rectList.get(j).contains(posx, posy)) {
                    for (int qubit : gos.get(i).getQubitIDs()) {
                        measuredQubits[qubit]--;
                    }
                    gos.remove(i);
                    invalidate();
                    saved = false;
                    return true;
                }
            }
        }
        return false;
    }

    public void addGate(int[] qubits, VisualOperator m) {
        for (int qubit : qubits) {
            if (qubit >= getDisplayedQubits()) return;
            if (!canAddGate(qubit))
                setLayoutParams(new LinearLayout.LayoutParams(getWidth() + 400, ViewGroup.LayoutParams.MATCH_PARENT));
            measuredQubits[qubit]++;
        }
        VisualOperator mm = m.copy();
        mm.setQubitIDs(qubits);
        gos.addLast(mm);
        invalidate();
        saved = false;
    }

    public boolean canAddGate(int qubit) {
        if (qubit < 0 || qubit > getDisplayedQubits()) {
            return false;
        }
        int gateNumber = 0;
        DisplayMetrics displayMetrics = new DisplayMetrics();
        ((Activity) (getContext())).getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int width = getWidth() < 1 ? displayMetrics.widthPixels : getWidth();
        outerLoop:
        for (int i = 0; i <= gos.size() + 1; i++) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (IntStream.of(gos.get(i).getQubitIDs()).noneMatch(x -> x == qubit)) continue;
                } else {
                    for (int k = 0; k < gos.size(); k++) {
                        if (gos.get(i).getQubitIDs()[k] == qubit) break;
                        if (k == gos.size() - 1) continue outerLoop;
                    }
                }
            } catch (IndexOutOfBoundsException e) {
            }
            gateNumber++;
            if (START_X + pxFromDp(super.getContext(), 2) + pxFromDp(super.getContext(), GATE_SIZE * 2) + gateNumber * pxFromDp(super.getContext(), GATE_SIZE * 3) > width)
                return false;
        }
        return true;
    }

    public boolean canAddMultiQubitGate(int[] qubits) {
        for (int q : qubits) {
            if (!canAddGate(q)) return false;
        }
        return true;
    }

    public int getDisplayedQubits() {
        int count = 0;
        for (int i = (int) START_Y; i < getLimit() && i <= pxFromDp(super.getContext(), STEP * MAX_QUBITS); i += (int) pxFromDp(super.getContext(), STEP)) {
            count++;
        }
        return count;
    }

    public int whichQubit(float posy) {
        int count = 0;
        for (int i = (int) START_Y; i < getLimit() && i <= pxFromDp(super.getContext(), STEP * MAX_QUBITS); i += (int) pxFromDp(super.getContext(), STEP)) {
            if (posy > i && posy < i + (int) pxFromDp(super.getContext(), STEP)) return count;
            count++;
        }
        return -1;
    }

    public short[] getMeasuredQubits() {
        return measuredQubits;
    }

    public LinkedList<VisualOperator> getOperators() {
        return gos;
    }

    public boolean removeLastGate() {
        if (gos.size() > 0) {
            VisualOperator v = gos.removeLast();
            for (int i = 0; i < v.getQubitIDs().length; i++) {
                measuredQubits[v.getQubitIDs()[i]]--;
            }
            invalidate();
            saved = false;
            return true;
        }
        return false;
    }

    public void clearScreen() {
        gos = new LinkedList<>();
        measuredQubits = null;
        measuredQubits = new short[MAX_QUBITS];
        invalidate();
        saved = false;
    }

    public int getLastUsedQubit() {
        int last = 0;
        for (int i = 0; i < measuredQubits.length; i++) {
            if (measuredQubits[i] > 0) {
                last = i;
            }
        }
        return last;
    }

    public int getUsedQubitsCount() {
        int count = 0;
        for (int i = 0; i < measuredQubits.length; i++) {
            if (measuredQubits[i] > 0) {
                count++;
            }
        }
        return count;
    }

    public void moveGate(float posx, float posy, boolean toRight) {
        int index = 0;
        VisualOperator operator = null;
        outer:
        for (int i = 0; i < gos.size(); i++) {
            List<RectF> rectList = gos.get(i).getRect();
            for (int j = 0; j < rectList.size(); j++) {
                if (rectList.get(j).contains(posx, posy)) {
                    operator = gos.get(i);
                    index = i;
                    break outer;
                }
            }
        }
        if (operator == null)
            return;
        int direction = toRight ? 1 : -1;
        outer:
        for (int i = index + direction; i < gos.size() && i > -1; i += direction) {
            int[] qubits = gos.get(i).getQubitIDs();
            for (int j = 0; j < qubits.length; j++) {
                for (int m = 0; m < operator.getQubitIDs().length; m++) {
                    if (qubits[j] == operator.getQubitIDs()[m]) {
                        gos.remove(index);
                        gos.add(i, operator);
                        invalidate();
                        saved = false;
                        return;
                    }
                }
            }
        }
    }

    public static float pxFromDp(final Context context, final float dp) {
        return dp * context.getResources().getDisplayMetrics().density;
    }

    public byte[] exportGatesLegacy(String name) {
        this.name = name;
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ObjectOutput output = new ObjectOutputStream(byteArrayOutputStream);
            output.writeObject(new GateSequence<VisualOperator>(gos, name));
            output.flush();
            return byteArrayOutputStream.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public JSONObject exportGates(String name) {
        try {
            return new GateSequence<VisualOperator>(gos, name).toJSON();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean importGates(JSONObject input) {
        try {
            GateSequence<VisualOperator> visualOperators = GateSequence.fromJSON(input);
            this.name = visualOperators.getName();
            gos = new LinkedList<>();
            measuredQubits = new short[MAX_QUBITS];
            boolean hadError = false;
            for (VisualOperator vo : ((LinkedList<VisualOperator>) visualOperators)) {
                if (vo == null) {
                    hadError = true;
                    continue;
                }
                addGate(vo.getQubitIDs(), vo);
            }
            invalidate();
            return !hadError;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean importGatesLegacy(Object input, String name) {
        try {
            if (input instanceof LinkedList && ((LinkedList<Object>) input).getFirst() instanceof VisualOperator) {
                gos = new LinkedList<>();
                measuredQubits = new short[MAX_QUBITS];
                invalidate();
                this.name = name.substring(0, name.lastIndexOf('.') < 1 ? name.length() : name.lastIndexOf('.'));
            } else return false;
            for (VisualOperator vo : ((LinkedList<VisualOperator>) input)) {
                addGate(vo.getQubitIDs(), vo);
            }
            invalidate();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public int optimizeCircuit() {
        if (gos.size() == 0) return 0;
        int counter = MAX_QUBITS;
        for (int i = MAX_QUBITS - 1; i > -1; i--) {
            if (measuredQubits[i] == 0) {
                counter--;
                for (int j = 0; j < gos.size(); j++) {
                    VisualOperator operator = gos.get(j);
                    int[] quids = operator.getQubitIDs();
                    for (int k = 0; k < quids.length; k++) {
                        if (quids[k] <= i) continue;
                        measuredQubits[quids[k] - 1]++;
                        measuredQubits[quids[k]]--;
                        quids[k]--;
                    }
                }
            }
        }
        invalidate();
        return counter;
    }

    public String openQASMExport() {
        StringBuilder builder = new StringBuilder();
        builder.append("OPENQASM 2.0;\n" +
                "include \"qelib1.inc\";\n\n");
        builder.append("qreg qubit[");
        builder.append(getLastUsedQubit() + 1);
        builder.append("];\n");
        builder.append("creg c[");
        builder.append(getLastUsedQubit() + 1);
        builder.append("];\n\n");
        for (VisualOperator visualOperator : gos) {
            try {
                builder.append(visualOperator.getOpenQASMSymbol());
                builder.append("\n");
            } catch (Exception e) {
                e.printStackTrace();
                builder.append("//Error while exporting the following: ");
                builder.append(visualOperator.getName());
                builder.append("\n");
            }
        }
        builder.append("measure qubit -> c;\n");
        return builder.toString();
    }

}