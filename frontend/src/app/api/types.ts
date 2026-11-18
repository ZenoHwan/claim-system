/**
 * The application's types, derived from the OpenAPI types generated in ./generated.
 *
 * springdoc marks every response field optional, so each type below states which fields the screens
 * rely on. The keys are checked against the generated type: if the backend renames or removes one,
 * this file stops compiling and `ng build` fails. Regenerate with `npm run api:generate`.
 */
import type { components as ClaimsApi } from './generated/claims';
import type { components as NotificationApi } from './generated/notification';
import type { components as ReportingApi } from './generated/reporting';

type C = ClaimsApi['schemas'];
type N = NotificationApi['schemas'];
type R = ReportingApi['schemas'];

/** T with the named fields guaranteed present. */
type Has<T, K extends keyof T> = Omit<T, K> & { [P in K]-?: NonNullable<T[P]> };
/** T with the named fields replaced by a stricter type (used for nested objects and lists). */
type Typed<T, V extends { [P in keyof T]?: unknown }> = Omit<T, keyof V> & V;

export type Role = NonNullable<C['LoginResponse']['role']>;
export type Door = C['LoginRequest']['door'];
export type ClaimStatus = NonNullable<C['Detail']['status']>;
export type LossCause = C['Property']['lossCause'];
export type PaymentMethod = C['RecordPaymentRequest']['method'];

export type LoginResponse = Has<
  C['LoginResponse'],
  'token' | 'userId' | 'name' | 'role' | 'market'
>;

export type Policy = Has<
  C['PolicyView'],
  | 'id'
  | 'policyNumber'
  | 'lineOfBusiness'
  | 'marketCode'
  | 'currency'
  | 'sumInsured'
  | 'effectiveFrom'
  | 'effectiveTo'
>;

export type SubmitClaimRequest = C['SubmitClaimRequest'];

export type ClaimListItem = Has<
  C['ListItem'],
  | 'id'
  | 'claimNumber'
  | 'lineOfBusiness'
  | 'marketCode'
  | 'currency'
  | 'status'
  | 'incidentDate'
  | 'claimantName'
  | 'submittedAt'
  | 'updatedAt'
>;

export type HistoryEntry = Has<C['HistoryEntry'], 'toStatus' | 'actorName' | 'changedAt'>;
export type InformationRequest = Has<
  C['InformationRequestView'],
  'id' | 'question' | 'status' | 'requestedByName' | 'requestedAt'
>;
export type DocumentView = Has<
  C['DocumentView'],
  'id' | 'fileName' | 'contentType' | 'sizeBytes' | 'uploadedByName' | 'uploadedAt'
>;
export type Assessment = Has<
  C['AssessmentView'],
  'findings' | 'recommendedAmount' | 'assessedByName' | 'assessedAt'
>;
export type Decision = Has<C['DecisionView'], 'outcome' | 'decidedByName' | 'decidedAt'>;
export type Payment = Has<
  C['PaymentView'],
  'payeeName' | 'amount' | 'currency' | 'status' | 'requestedAt'
>;

export type ClaimDetail = Typed<
  Has<
    C['Detail'],
    | 'id'
    | 'claimNumber'
    | 'policyId'
    | 'policyNumber'
    | 'sumInsured'
    | 'lineOfBusiness'
    | 'marketCode'
    | 'currency'
    | 'status'
    | 'incidentDate'
    | 'incidentDescription'
    | 'claimantId'
    | 'claimantName'
    | 'version'
    | 'submittedAt'
    | 'updatedAt'
  >,
  {
    motor?: Required<C['MotorView']>;
    property?: Required<C['PropertyView']>;
    history: HistoryEntry[];
    informationRequests: InformationRequest[];
    documents: DocumentView[];
    /** Staff only. */
    assessment?: Assessment;
    decision?: Decision;
    payment?: Payment;
  }
>;

export type Workload = Typed<
  Has<R['Workload'], 'market' | 'openClaims' | 'unassigned'>,
  {
    officers: Has<R['OfficerWorkload'], 'officerId' | 'officerName' | 'open' | 'byStatus'>[];
    ageing: Required<R['Ageing']>;
  }
>;
export type Performance = Has<R['Performance'], 'market' | 'decidedLast30Days'>;
export type Exposure = Typed<R['Exposure'], { rows: Required<R['ExposureRow']>[] }>;

export type NotificationItem = Has<
  N['Notification'],
  'id' | 'recipientId' | 'claimId' | 'claimNumber' | 'type' | 'message' | 'createdAt' | 'read'
>;
export type Inbox = Typed<Has<N['Inbox'], 'unreadCount'>, { items: NotificationItem[] }>;
