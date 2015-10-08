package com.devmoroz.moneyme.models;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import java.sql.Date;

@DatabaseTable(tableName = "incomes")
public class Income {

    @DatabaseField(generatedId = true)
    private int id;

    @DatabaseField(canBeNull = false,dataType = DataType.STRING)
    private String name;

    @DatabaseField(canBeNull = true)
    private String notes;

    @DatabaseField(dataType = DataType.SQL_DATE)
    private Date dateOfReceipt;

    @DatabaseField(canBeNull = false,dataType = DataType.DOUBLE)
    private double amount;

    @DatabaseField(canBeNull = false)
    private int currency;

    public Income() {
    }
}