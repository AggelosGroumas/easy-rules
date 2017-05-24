package org.easyrules.exceptions;

/**
 * @author <a href="mailto:a.groumas@app-art.gr">Aggelos Groumas</a>
 */
public class RuleNameNotFoundInLogicalConnectionException extends Exception {

    public String message;

    public RuleNameNotFoundInLogicalConnectionException(String message) {
        this.message = message;
    }

    public RuleNameNotFoundInLogicalConnectionException() {
    }

    @Override
    public String getMessage() {
        return message;
    }

}
