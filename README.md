# API Gateway to SQS
Training project for the integration AWS API Gateway with SQS created with CDK

The `cdk.json` file tells the CDK Toolkit how to execute your app.

It is a [Maven](https://maven.apache.org/) based project, so you can open this project with any Maven compatible Java IDE to build and run tests.

## Integration implementation
There are two ways to integration creation:
1. Via AWS Solution Constructs (more info [here](https://docs.aws.amazon.com/solutions/latest/constructs/aws-apigateway-sqs.html))
2. Created API Gateway, SQS queue, and integration between them

In this project, integration implemented with transforming the API Gateway's request payload into a SQS message body.
Also in a second way of the implementation, SQS message will contain request path param value in the body, 
and the authorization header value will be added as a message attribute. This is defined in the 
```
requestTemplates(Map.of("application/json",
"Action=SendMessage&MessageBody=#set($input.path('$').someId=$util"
+ ".escapeJavaScript($input.params('someId')))$input.json('$')"
+ "&MessageAttribute.1.Name=Authorization"
+ "&MessageAttribute.1.Value.StringValue=$input.params('Authorization')"
+ "&MessageAttribute.1.Value.DataType=String"))
```
Where `$input.path(x)` takes a JSONPath expression string (x) and returns a JSON object representation of the result.
This allows you to access and manipulate elements of the payload natively in Apache Velocity Template Language (https://velocity.apache.org/engine/devel/vtl-reference.html).
## Conclusion
Basically we can use 'ApiGatewayToSqs' AWS Solutions Construct that implements an Amazon API Gateway
connected to an Amazon SQS queue pattern with just couple lines of code. Or we can implement an API Gateway
and SQS queue and define an integration between.
## Useful commands

* `mvn package`     compile and run tests
* `cdk ls`          list all stacks in the app
* `cdk synth`       emits the synthesized CloudFormation template
* `cdk deploy`      deploy this stack to your default AWS account/region
* `cdk diff`        compare deployed stack with current state
* `cdk docs`        open CDK documentation
