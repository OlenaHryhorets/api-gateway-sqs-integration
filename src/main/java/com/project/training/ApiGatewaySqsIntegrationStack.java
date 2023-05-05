package com.project.training;

import java.util.List;
import java.util.Map;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.apigateway.AwsIntegration;
import software.amazon.awscdk.services.apigateway.ConnectionType;
import software.amazon.awscdk.services.apigateway.EndpointType;
import software.amazon.awscdk.services.apigateway.IntegrationOptions;
import software.amazon.awscdk.services.apigateway.IntegrationResponse;
import software.amazon.awscdk.services.apigateway.MethodOptions;
import software.amazon.awscdk.services.apigateway.MethodResponse;
import software.amazon.awscdk.services.apigateway.PassthroughBehavior;
import software.amazon.awscdk.services.apigateway.Resource;
import software.amazon.awscdk.services.apigateway.RestApi;
import software.amazon.awscdk.services.apigateway.RestApiProps;
import software.amazon.awscdk.services.apigateway.StageOptions;
import software.amazon.awscdk.services.iam.Policy;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.sqs.Queue;
import software.amazon.awsconstructs.services.apigatewaysqs.ApiGatewayToSqs;
import software.amazon.awsconstructs.services.apigatewaysqs.ApiGatewayToSqsProps;
import software.constructs.Construct;

public class ApiGatewaySqsIntegrationStack extends Stack {
  public static final String SQS_NAME = "sqs-proxy";
  public ApiGatewaySqsIntegrationStack(final Construct scope, final String id) {
    this(scope, id, null);
  }

  public ApiGatewaySqsIntegrationStack(final Construct scope, final String id,
                                       final StackProps props) {
    super(scope, id, props);

    // 1. API Gateway to SQS integration created by AWS construct:
    new ApiGatewayToSqs(this, "ApiGatewayToSqsPattern", new ApiGatewayToSqsProps.Builder()
        .deployDeadLetterQueue(false)
        .allowCreateOperation(true)
        .createRequestTemplate("Action=SendMessage&MessageBody=$input.body")
        .build());

    // 2. API Gateway to SQS integration created by our definition:
    Queue.Builder.create(this, SQS_NAME)
        .queueName(SQS_NAME)
        .build();

    RestApi restapi = new RestApi(this, "sqs-proxy-api-gateway",
        RestApiProps.builder()
            .restApiName("sqs-proxy-api-gateway")
            .cloudWatchRole(true)
            .deployOptions(StageOptions.builder()
                .stageName("prod")
                .cachingEnabled(false)
                .build())
            .description("API Gateway to SQS integration")
            .endpointTypes(List.of(EndpointType.REGIONAL))
            .build());
    // Define API
    Resource apiVersion = restapi.getRoot().addResource("v1");
    Resource sqsEndpoint = apiVersion.addResource("message");
    Resource someIdPath = sqsEndpoint.addResource("{someId}");
    someIdPath.addMethod("POST", createIntegrationToSqs(this),
        MethodOptions.builder()
            .apiKeyRequired(true)
            .methodResponses(List.of(MethodResponse.builder()
                .statusCode("200")
                .build()))
            .build()
    );
  }

  private AwsIntegration createIntegrationToSqs(Construct scope) {
    return AwsIntegration.Builder.create()
        .service("sqs")
        .path(ACCOUNT_ID + "/" + SQS_NAME) // replace ACCOUNT_ID with your account ID value
        .integrationHttpMethod("POST")
        .options(IntegrationOptions.builder()
            .connectionType(ConnectionType.INTERNET)
            .passthroughBehavior(PassthroughBehavior.NEVER)
            .requestTemplates(
                Map.of("application/json",
                    "Action=SendMessage&MessageBody=#set($input.path('$').someId=$util"
                        + ".escapeJavaScript($input.params('someId')))$input.json('$')"
                        + "&MessageAttribute.1.Name=Authorization"
                        + "&MessageAttribute.1.Value.StringValue=$input.params('Authorization')"
                        + "&MessageAttribute.1.Value.DataType=String"))
            .requestParameters(Map.of("integration.request.header.Content-Type","'application/x-www-form-urlencoded'"))
            .credentialsRole(Role.Builder.create(scope, "sqs-proxy-role")
                .roleName("api-gw-sqs-role")
                .inlinePolicies(Map.of("api-gw-sqs-policy",
                    Policy.Builder.create(scope, "sqs-proxy-policy")
                        .statements(List.of(PolicyStatement.Builder.create()
                            .actions(List.of("sqs:SendMessage"))
                            .resources(List.of(String.format("arn:aws:sqs:%s:%s:%s", REGION, ACCOUNT_ID, SQS_NAME)))
                            .build()))
                        .build().getDocument()))
                .assumedBy(new ServicePrincipal("apigateway.amazonaws.com"))
                .build())
            .integrationResponses(List.of(
                    IntegrationResponse.builder()
                        .statusCode("200")
                        .build(),
                    IntegrationResponse.builder()
                        .statusCode("500")
                        .selectionPattern("5\\d{2}")
                        .responseTemplates(Map.of("text/html", "Error"))
                        .build()
                )
            )
            .build())
        .build();
  }
}
