
# B9lab Corda course notes

## Module 3

### Expand the example

> To find out
> - How do you pass a known state from the vault when launching a flow?

I can use simply tx id, and in a code pull the state like so:
`val ourStateRef = StateRef(SecureHash.parse(txId), 0)`

or better way:

> You can query an IOU by its linearId:
>
> ```
> UUID uuid = UUID.fromString("some Id");
> QueryCriteria queryCriteria = new QueryCriteria.LinearStateQueryCriteria()
>                                  .withUuid(Collections.singletonList(uuid));
>
> getServiceHub().getVaultService().queryBy(IOUState.class, queryCriteria);
> ```

> - What notary do you choose?

> Generally you should specify which notary you're going to use when you create new states:
> ``` 
>  String notaryName = "O=Notary,L=London,C=GB";
>  Party notary = getServiceHub().getNetworkMapCache().getNotary(CordaX500Name.parse(notaryName));
> ```
>  If you're consuming states (i.e. your transaction has inputs); you should extract the notary from the input state:
> ```  
>  StateAndRef<IOUState> txInput;
>  Party notary = txInput.getState().getNotary();
> ```
  
> - How do you launch from the shell a flow with a known state from the vault?

First on PartyA:
```
flow start ExampleFlow$Initiator iouValue: 50, borrower: "O=PartyB,L=New York,C=US"
> Flow completed with result: SignedTransaction(id=B8B9D36C87112E1C176F105F5D570FDE61B8AC2453568429C973D18B57B7C5D5)
```

then on PartyB:
```
run vaultQuery contractStateType: com.example.state.IOUState
# copy UUID
`flow start PaybackFlow$Initiator inputLinearId: "08b46ba6-670a-47a2-b77a-ea201cbcad5d"`
```
