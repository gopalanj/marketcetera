package org.marketcetera.core.position;

import java.math.BigDecimal;

import org.apache.commons.lang.StringUtils;
import org.marketcetera.core.position.impl.Messages;
import org.marketcetera.core.position.impl.PositionEngineImpl;
import org.marketcetera.messagehistory.ReportHolder;
import org.marketcetera.trade.ExecutionReport;
import org.marketcetera.trade.MSymbol;
import org.marketcetera.trade.OrderStatus;
import org.marketcetera.trade.ReportBase;
import org.marketcetera.trade.ReportID;
import org.marketcetera.util.misc.ClassVersion;

import ca.odell.glazedlists.EventList;
import ca.odell.glazedlists.FilterList;
import ca.odell.glazedlists.FunctionList;
import ca.odell.glazedlists.FunctionList.Function;
import ca.odell.glazedlists.matchers.Matcher;

/* $License$ */

/**
 * Factory for creating position engines.
 * 
 * @author <a href="mailto:will@marketcetera.com">Will Horn</a>
 * @version $Id$
 * @since $Release$
 */
@ClassVersion("$Id$")
public class PositionEngineFactory {

    /**
     * Create a position engine for a dynamic list of trades.
     * 
     * @param trades
     *            list of trades
     * @return a position engine
     */
    public static PositionEngine create(EventList<Trade> trades) {
        FilterList<Trade> validTrades = new FilterList<Trade>(trades, new ValidationMatcher());
        return new PositionEngineImpl(validTrades);
    }

    /**
     * Convenience method when using reports.
     * 
     * @param reports
     *            list of reports
     * @return a position engine
     */
    public static PositionEngine createFromReports(EventList<ReportBase> reports) {
        FilterList<ReportBase> fills = new FilterList<ReportBase>(reports, new FillMatcher());
        FunctionList<ReportBase, Trade> trades = new FunctionList<ReportBase, Trade>(fills,
                new TradeFunction());
        return create(trades);
    }

    /**
     * Convenience method when using report holders.
     * 
     * @param holders
     *            list of report holders
     * @return a position engine
     */
    public static PositionEngine createFromReportHolders(EventList<ReportHolder> holders) {
        FunctionList<ReportHolder, ReportBase> reports = new FunctionList<ReportHolder, ReportBase>(
                holders, new ReportExtractor());
        return createFromReports(reports);
    }

    /**
     * Function extracting reports from report holders.
     */
    @ClassVersion("$Id$")
    private final static class ReportExtractor implements Function<ReportHolder, ReportBase> {

        @Override
        public ReportBase evaluate(ReportHolder sourceValue) {
            return sourceValue.getReport();
        }

    }

    /**
     * Matcher that matches fills and partial fills.
     */
    @ClassVersion("$Id$")
    private final static class FillMatcher implements Matcher<ReportBase> {

        @Override
        public boolean matches(ReportBase item) {
            OrderStatus orderStatus = item.getOrderStatus();
            return item instanceof ExecutionReport
                    && (orderStatus == OrderStatus.PartiallyFilled || orderStatus == OrderStatus.Filled);
        }
    }

    /**
     * Matcher that matches valid trades, as specified by the {@link Trade}
     * interface.
     */
    @ClassVersion("$Id$")
    private final static class ValidationMatcher implements Matcher<Trade> {

        @Override
        public boolean matches(Trade item) {
            if (notEmpty(item.getSymbol()) && notEmpty(item.getTraderId())
                    && positive(item.getPrice()) && notZero(item.getQuantity())) {
                return true;
            } else {
                Messages.VALIDATION_MATCHER_INVALID_TRADE.error(this, item);
                return false;
            }
        }

        private boolean notEmpty(String string) {
            return StringUtils.isNotEmpty(string);
        }

        private boolean notNull(Object object) {
            return object != null;
        }

        private boolean notZero(BigDecimal number) {
            return notNull(number) && number.signum() != 0;
        }

        private boolean positive(BigDecimal number) {
            return notNull(number) && number.signum() == 1;
        }
    }

    /**
     * Function mapping execution reports to trades.
     * <p>
     * Note that even though the parameter type is ReportBase, the source
     * elements must be ExecutionReport that represent fills or partial fills.
     * Use {@link FillMatcher} to ensure this.
     */
    @ClassVersion("$Id$")
    private final static class TradeFunction implements Function<ReportBase, Trade> {

        @Override
        public Trade evaluate(ReportBase sourceValue) {
            return fromReport((ExecutionReport) sourceValue);
        }

        private Trade fromReport(ExecutionReport report) {
            return new ExecutionReportAdapter(report);
        }
    }

    /**
     * Adapts an {@link ExecutionReport} to be used as a Trade.
     * 
     * Note: this class may break the contract of Trade, knowing that invalid
     * trades will be filtered out in
     * {@link PositionEngineFactory#create(EventList)}.
     */
    private final static class ExecutionReportAdapter implements Trade {

        private final ExecutionReport mReport;

        /**
         * Constructor.
         * 
         * @param report
         *            execution report to adapt
         */
        public ExecutionReportAdapter(ExecutionReport report) {
            mReport = report;
        }

        @Override
        public String getSymbol() {
            MSymbol symbol = mReport.getSymbol();
            return symbol == null ? null : symbol.toString();
        }

        @Override
        public String getAccount() {
            return mReport.getAccount();
        }

        @Override
        public String getTraderId() {
            // TODO: update once reports support trader id
            return "Yoram"; //$NON-NLS-1$
        }

        @Override
        public BigDecimal getPrice() {
            return mReport.getLastPrice();
        }

        @Override
        public BigDecimal getQuantity() {
            BigDecimal lastQuantity = mReport.getLastQuantity();
            if (lastQuantity != null) {
                switch (mReport.getSide()) {
                case Buy:
                    return lastQuantity;
                case Sell:
                case SellShort:
                case SellShortExempt:
                    return lastQuantity.negate();
                }
            }
            return null;
        }

        @Override
        public long getSequenceNumber() {
            ReportID reportId = mReport.getReportID();
            return reportId == null ? null : reportId.longValue();
        }

        @Override
        public String toString() {
            return Messages.EXECUTION_REPORT_ADAPTER_TO_STRING.getText(String.valueOf(getSymbol()),
                    String.valueOf(getAccount()), String.valueOf(getTraderId()), String
                            .valueOf(getPrice()), String.valueOf(getQuantity()), String
                            .valueOf(getSequenceNumber()), mReport.toString());
        }

    }
}