package org.example.controllers;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseEvent;
import javafx.util.Callback;
import org.example.components.TransactionComponent;
import org.example.dialog.*;
import org.example.models.MonthlyFinance;
import org.example.models.Transaction;
import org.example.models.User;
import org.example.utils.SqlUtil;
import org.example.views.DashboardView;
import org.example.views.LoginView;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;

public class DashboardController {
    private final int recentTransactionSize = 5;

    private DashboardView dashboardView;
    private User user;

    private List<Transaction> recentTransactions, currentTransactionByYear;
    private int currentPage;
    private int currentYear;

    public DashboardController(DashboardView dashboardView){
        this.dashboardView = dashboardView;
        currentYear = dashboardView.getYearComboBox().getValue();
        this.currentPage=0;
        fetchUserData();
        initialize();
    }

    public void fetchUserData(){
        dashboardView.getLoadingAnimationPane().setVisible(true);
        dashboardView.getRecentTransactionBox().getChildren().clear();
        user = SqlUtil.getUserByEmail(dashboardView.getEmail());

        currentTransactionByYear = SqlUtil.getAllTransactionsByUserId(user.getId(), currentYear, null);
        calculateDistinctYears();
        calculateBalanceAndIncomeAndExpense();

        dashboardView.getTransactionTable().setItems(calculateMonthlyFinances());
        createRecentTransactionComponents();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    Thread.sleep(1000);
                    dashboardView.getLoadingAnimationPane().setVisible(false);
                }catch(InterruptedException e){
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void calculateDistinctYears(){
        List<Integer> distinctYears = SqlUtil.getAllDistinctYears(user.getId());
        for(Integer integer : distinctYears){
            if(!dashboardView.getYearComboBox().getItems().contains(integer)){
                dashboardView.getYearComboBox().getItems().add(integer);
            }
        }
    }

    private void calculateBalanceAndIncomeAndExpense(){
        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;
        if(currentTransactionByYear != null){
            for(Transaction transaction : currentTransactionByYear){
                BigDecimal transactionAmount = BigDecimal.valueOf(transaction.getTransactionAmount());
                if(transaction.getTransactionType().equalsIgnoreCase("income")){
                    totalIncome = totalIncome.add(transactionAmount);
                }else{
                    totalExpense = totalExpense.add(transactionAmount);
                }
            }
        }
        totalIncome = totalIncome.setScale(2, RoundingMode.HALF_UP);
        totalExpense = totalExpense.setScale(2, RoundingMode.HALF_UP);
        BigDecimal currentBalance = totalIncome.subtract(totalExpense);
        currentBalance = currentBalance.setScale(2, RoundingMode.HALF_UP);

        //update view
        dashboardView.getTotalExpense().setText("₹" + totalExpense);
        dashboardView.getTotalIncome().setText("₹" + totalIncome);
        dashboardView.getCurrentBalance().setText("₹" + currentBalance);
    }

    private void createRecentTransactionComponents(){
        recentTransactions = SqlUtil.getRecentTransactionByUserId(
                user.getId(),
                0,
                currentPage,
                recentTransactionSize
        );
        if(recentTransactions == null) return;

        for(Transaction transaction: recentTransactions){
            dashboardView.getRecentTransactionBox().getChildren().add(
                    new TransactionComponent(this, transaction)
            );
        }
    }

    private ObservableList<MonthlyFinance> calculateMonthlyFinances(){
        double[] incomeCounter = new double[12];
        double[] expenseCounter = new double[12];

        for(Transaction transaction : currentTransactionByYear){
            LocalDate transactionDate = transaction.getTransactionDate();
            if(transaction.getTransactionType().equalsIgnoreCase("income")){
                incomeCounter[transactionDate.getMonth().getValue() -1] += transaction.getTransactionAmount();
            }
            else{
                expenseCounter[transactionDate.getMonth().getValue() -1] += transaction.getTransactionAmount();
            }
        }

        ObservableList<MonthlyFinance> monthlyFinances = FXCollections.observableArrayList();
        for(int i=0; i<12; i++){
            MonthlyFinance monthlyFinance = new MonthlyFinance(
                    Month.of(i+1).name(),
                    new BigDecimal(String.valueOf(incomeCounter[i])),
                    new BigDecimal(String.valueOf(expenseCounter[i]))
            );
            monthlyFinances.add(monthlyFinance);

        }
        return monthlyFinances;
    }

    private void initialize(){
        addMenuActions();
        addRecentTransactionActions();
        addComboBoxActions();
        addViewChartActions();
        addTableActions();
    }

    private void addMenuActions(){
        dashboardView.getCreateCategoryMenuItem().setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                new CreateNewCategoryDialog(user).showAndWait();
            }
        });

        dashboardView.getViewCategoriesMenuItem().setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                new ViewOrEditTransactionCategoryDialog(user, DashboardController.this).showAndWait();
            }
        });

        dashboardView.getLogoutMenuItem().setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                new LoginView().show();
            }
        });
    }

    private void addRecentTransactionActions(){
        dashboardView.getAddTransactionButton().setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                new CreateOrEditTransactionDialog(DashboardController.this, false).showAndWait();
            }
        });
    }

    private void addComboBoxActions(){
        dashboardView.getYearComboBox().setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                //update current year
                currentYear = dashboardView.getYearComboBox().getValue();
                fetchUserData();

            }
        });
    }

    private void addViewChartActions(){
        dashboardView.getViewChartButton().setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                new ViewChartDialog(user, dashboardView.getTransactionTable().getItems()).showAndWait();
            }
        });
    }

    private void addTableActions(){
        dashboardView.getTransactionTable().setRowFactory(new Callback<TableView<MonthlyFinance>, TableRow<MonthlyFinance>>() {
            @Override
            public TableRow<MonthlyFinance> call(TableView<MonthlyFinance> monthlyFinanceTableView) {
                TableRow<MonthlyFinance> row = new TableRow<>();
                row.setOnMouseClicked(new EventHandler<MouseEvent>() {
                    @Override
                    public void handle(MouseEvent mouseEvent) {
                        if(!row.isEmpty() && mouseEvent.getClickCount() == 2){
                            MonthlyFinance monthlyFinance = row.getItem();
                            new ViewTransactionsDialog(DashboardController.this, monthlyFinance.getMonth()).showAndWait();
                        }
                    }
                });
                return row;
            }
        });
    }

    public User getUser(){
        return user;
    }

    public int getCurrentYear() {
        return currentYear;
    }
}
