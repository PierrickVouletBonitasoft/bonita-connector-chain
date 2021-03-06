/**
 * 
 */

package org.bonitasoft.connectors.chain;

import com.chain.api.MockHsm;
import com.chain.api.Transaction;
import com.chain.exception.BadURLException;
import com.chain.exception.BuildException;
import com.chain.exception.ChainException;
import com.chain.http.Client;
import com.chain.signing.HsmSigner;
import com.chain.api.Transaction.Action.ControlWithAccount;
import com.chain.api.Transaction.Action.SpendFromAccount;

import org.apache.commons.lang3.StringUtils;
import org.bonitasoft.engine.connector.ConnectorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * The connector execution will follow the steps 1 - setInputParameters() --> the connector receives
 * input parameters values 2 - validateInputParameters() --> the connector can validate input
 * parameters values 3 - connect() --> the connector can establish a connection to a remote server
 * (if necessary) 4 - executeBusinessLogic() --> execute the connector 5 - getOutputParameters() -->
 * output are retrieved from connector 6 - disconnect() --> the connector can close connection to
 * remote server (if any)
 */
public class ChainCreateBaseExchangeAssetConnector
        extends AbstractChainCreateBaseExchangeAssetImpl {

    @Override
    protected void executeBusinessLogic() throws ConnectorException {
        // Get access to the connector input parameters
        // getUrl();
        // getAccountToken();
        // getAlias();

        final Logger logger = LoggerFactory.getLogger(ChainCreateBaseExchangeAssetConnector.class);

        Client client;

        if (StringUtils.isNotEmpty(getUrl())) {
            try {
                if (StringUtils.isNotEmpty(getAccountToken())) {
                    client = new Client(getUrl(), getAccountToken());
                } else {
                    client = new Client(getUrl());
                }
            } catch (BadURLException e) {
                throw new ConnectorException("Error while creating Chain client", e.getCause());
            }
        } else {
            client = new Client();
        }

        try {

            
            //Retrieve signing key
            MockHsm.Key accountKey = new MockHsm.Key.QueryBuilder()
                    .setAliases(Arrays.asList(getKeyAccountAlias())).execute(client).next();

            // Add key to signer 
            HsmSigner.addKey(accountKey, MockHsm.getSignerClient(client));

            List<List<String>> listReferenceData = getReferenceData();

            // Create output part of the transaction
            ControlWithAccount outputTransaction = new Transaction.Action.ControlWithAccount()
                    .setAccountAlias(getAccountAlias()).setAssetId(getReceiveAssetId())
                    .setAmount(Long.parseLong(getReceiveAmount()));
            
            // Create input part of the transaction
            SpendFromAccount inputTransaction = new Transaction.Action.SpendFromAccount()
                    .setAssetId(getSpendAssetId()).setAccountAlias(getAccountAlias())
                    .setAmount(Long.parseLong(getSpendAmount()));

            // Add references data to the transaction
            for (List<String> row : listReferenceData) {
                if (row.size() == 2) {
                    outputTransaction.addReferenceDataField(row.get(0), row.get(1));
                }
            }

            // Build the transaction
            Transaction.Template partialTransaction = new Transaction.Builder()
                    .addAction(inputTransaction)
                    .addAction(outputTransaction).build(client);

            // Sign the transaction
            Transaction.Template signedPartialTransaction =
                    HsmSigner.sign(partialTransaction.allowAdditionalActions());

            // Return JSON Representation of the transaction
            setBaseTransaction(new BaseTransactionResponse(signedPartialTransaction.rawTransaction));
            
        } catch (ChainException e) {
            if (e instanceof BuildException) {
                BuildException buildEx = (BuildException) e;
                setBaseTransaction(
                        new BaseTransactionResponse(buildEx.code, dataErrorsToString(buildEx)));
            } else {
                throw new ConnectorException("Error while exchange multi trade, cause: ", e.getCause());
            }
        }
    }
    
    private String dataErrorsToString(BuildException buildEx) {

        if (buildEx.data == null || buildEx.data.actionErrors.size() == 0) {
            return buildEx.detail;
        }

        StringBuilder builder = new StringBuilder();
        for (BuildException.ActionError error : buildEx.data.actionErrors) {
            builder.append(error.chainMessage);
        }
        return builder.toString();

    }

    @Override
    public void connect() throws ConnectorException {
        // [Optional] Open a connection to remote server

    }

    @Override
    public void disconnect() throws ConnectorException {
        // [Optional] Close connection to remote server

    }

}
