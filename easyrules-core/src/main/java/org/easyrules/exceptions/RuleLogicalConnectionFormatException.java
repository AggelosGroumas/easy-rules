package org.easyrules.exceptions;

/**
 * @author <a href="mailto:a.groumas@app-art.gr">Aggelos Groumas</a>
 */
public class RuleLogicalConnectionFormatException extends Exception {

    public String message;

    public RuleLogicalConnectionFormatException() {
    }

    public RuleLogicalConnectionFormatException(String message) {
        super(message);
        this.message = message;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
