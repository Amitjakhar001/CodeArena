package dev.codearena.app.domain;

public class SampleTestCase {
    private String input;
    private String expectedOutput;
    private boolean hidden;

    public SampleTestCase() {}

    public SampleTestCase(String input, String expectedOutput, boolean hidden) {
        this.input = input;
        this.expectedOutput = expectedOutput;
        this.hidden = hidden;
    }

    public String getInput() { return input; }
    public void setInput(String input) { this.input = input; }

    public String getExpectedOutput() { return expectedOutput; }
    public void setExpectedOutput(String expectedOutput) { this.expectedOutput = expectedOutput; }

    public boolean isHidden() { return hidden; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }
}
