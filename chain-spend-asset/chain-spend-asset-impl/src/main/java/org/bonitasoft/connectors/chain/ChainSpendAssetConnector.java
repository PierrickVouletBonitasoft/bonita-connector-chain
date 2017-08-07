/**
 * 
 */

package org.bonitasoft.connectors.chain;

import com.chain.api.MockHsm;
import com.chain.api.Transaction;
import com.chain.api.Transaction.Action.ControlWithAccount;
import com.chain.api.Transaction.Action.SpendFromAccount;
import com.chain.exception.BadURLException;
import com.chain.exception.BuildException;
import com.chain.exception.ChainException;
import com.chain.http.Client;
import com.chain.signing.HsmSigner;

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
public class ChainSpendAssetConnector extends AbstractChainSpendAssetImpl {

    @Override
    protected void executeBusinessLogic() throws ConnectorException {
        // Get access to the connector input parameters
        // getUrl();
        // getAccountToken();
        // getAlias();

        final Logger logger = LoggerFactory.getLogger(ChainSpendAssetConnector.class);

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
            if (StringUtils.isNotEmpty(getKeyAssetAlias())) {
                MockHsm.Key assetKey = new MockHsm.Key.QueryBuilder()
                        .setAliases(Arrays.asList(getKeyAssetAlias())).execute(client).next();
                HsmSigner.addKey(assetKey, MockHsm.getSignerClient(client));
            }
            

            MockHsm.Key accountFromKey = new MockHsm.Key.QueryBuilder()
                    .setAliases(Arrays.asList(getKeyAccountFromAlias())).execute(client).next();

            MockHsm.Key accountToKey = new MockHsm.Key.QueryBuilder()
                    .setAliases(Arrays.asList(getKeyAccountToAlias())).execute(client).next();

            HsmSigner.addKey(accountFromKey, MockHsm.getSignerClient(client));
            HsmSigner.addKey(accountToKey, MockHsm.getSignerClient(client));
            
            ControlWithAccount outputTransaction = null;
            if (StringUtils.isNotEmpty(getAssetAlias())) {
                 outputTransaction = new Transaction.Action.ControlWithAccount()
                .setAccountAlias(getAccountToAlias()).setAssetAlias(getAssetAlias())
                .setAmount(Long.parseLong(getAmount()));
            } else if (StringUtils.isNotEmpty(getAssetId())) {
                outputTransaction = new Transaction.Action.ControlWithAccount()
                        .setAccountAlias(getAccountToAlias()).setAssetId(getAssetId())
                        .setAmount(Long.parseLong(getAmount()));
            }
                   
            
            List<List<String>> listReferenceData = getReferenceData();
            
           for(List<String> referenceData : listReferenceData) {
                if (referenceData.size() == 2) {
                    outputTransaction.addReferenceDataField(referenceData.get(0),
                            referenceData.get(1));
                }
           }
           SpendFromAccount inputTransaction = null;
           if (StringUtils.isNotEmpty(getAssetAlias())) {
             inputTransaction = new Transaction.Action.SpendFromAccount()
               .setAssetAlias(getAssetAlias()).setAccountAlias(getAccountFromAlias())
               .setAmount(Long.parseLong(getAmount()));
           } else if (StringUtils.isNotEmpty(getAssetId())) {
               inputTransaction = new Transaction.Action.SpendFromAccount()
                       .setAssetId(getAssetId()).setAccountAlias(getAccountFromAlias())
                       .setAmount(Long.parseLong(getAmount()));
           }

            Transaction.Template issueTransaction = new Transaction.Builder()
                    .addAction(inputTransaction)
                    .addAction(outputTransaction)
                    .build(client);

            Transaction.Template signedIssuance = HsmSigner.sign(issueTransaction);
            Transaction.SubmitResponse submitResponse = Transaction.submit(client, signedIssuance);

            org.bonitasoft.connectors.chain.TransactionResponse formattedResult =
                    new org.bonitasoft.connectors.chain.TransactionResponse(submitResponse.id);
            setTransactionResponse(formattedResult);
        } catch (ChainException e) {
            if (e instanceof BuildException) {
                BuildException buildEx = (BuildException) e;
                setTransactionResponse(
                        new TransactionResponse(buildEx.code, dataErrorsToString(buildEx)));
            } else {
                throw new ConnectorException("Error while getting the Chain account", e.getCause());
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
