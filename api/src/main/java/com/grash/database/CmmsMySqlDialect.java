package com.grash.database;
import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.dialect.MySQLDialect;
import org.hibernate.type.StandardBasicTypes;
/** Like-search accent folding uses MySQL collation, not a nonexistent unaccent function. */
public class CmmsMySqlDialect extends MySQLDialect {
    @Override public void initializeFunctionRegistry(FunctionContributions functions) {
        super.initializeFunctionRegistry(functions);
        functions.getFunctionRegistry().registerPattern("unaccent","(?1 collate utf8mb4_0900_ai_ci)",
            functions.getTypeConfiguration().getBasicTypeRegistry().resolve(StandardBasicTypes.STRING));
    }
}
