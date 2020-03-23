
# B9lab Corda course notes

## Module 3

### Expand the example

> To find out
> - How do you pass a known state from the vault when launching a flow?

I can use simply tx id, and in a code pull the state like so:
`val ourStateRef = StateRef(SecureHash.parse(txId), 0)`

> - What notary do you choose?

I will use default notary.  
But I'm also wondering if there is a way not to use it?
When borower want to replay, he don't need lender signature. 
If tx will be valid then he should be able to repay - we talking about situation when we repay 100%.

> - How do you launch from the shell a flow with a known state from the vault?

First on PartyA:
```
flow start ExampleFlow$Initiator iouValue: 50, borower: "O=PartyB,L=New York,C=US"
> Flow completed with result: SignedTransaction(id=B8B9D36C87112E1C176F105F5D570FDE61B8AC2453568429C973D18B57B7C5D5)
```

then with knownw tx id, on PartyB:
```
`flow start PaybackFlow$Initiator loanTxId: "236F4610D04A993F337B8E261E0E2B649A2AAF45403D12F215EBC5CAA11221BE"`
```
