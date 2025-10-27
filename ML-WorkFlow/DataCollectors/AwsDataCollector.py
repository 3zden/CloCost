import  boto3
import pandas as pd
from datetime import datetime, timedelta



class AwsDataCollector:
    def __init__(self,region='us-east-1'):
        self.ec2=boto3.client('ec2',region_name=region)
        self.s3=boto3.client('s3',region_name=region)
        self.ce=boto3.client('ce',region_name=region)
        self.cloudwatch=boto3.client('cloudwatch',region_name=region)
        
        def collect_data(self,days_back=60):
            data={
                'costs':self.get_details_costs(days_back),
                'resources':self.get_all_resources(),
                'utilization':self.get_resource_utilization(),
                'metadata':self.get_resource_metadata()
            }
            return data
        
        def get_details_costs(self,days_back):
            end_date=datetime.now().date()
            start_date=end_date - timedelta(days=days_back)
            costs=[]
            try:
                response=self.ce.get_cost_and_usage(
                    TimePeriod={
                        'Start':str(start_date),
                        'End':str(end_date)
                    }
                    Granularity='Daily',
                    Metrics=['UnblendedCost','UsageQuantity','NormalizedUsageAmount']
                    GroupBy=[
                        {'Type':'DIMENSION','Key':'SERVICE'},
                        {'Type':'DIMENSION','Key':'USAGE_TYPE'}
                    ]
                )
                for day_result in response['ResultsByTime']:
                    date=day_result['TimePeriod']['Start']
                    for group in day_result['Groups']:
                        service=group['Keys'][0]
                        usage_type=group['Keys'][1]
                        costs.append(
                            {
                                'date':date,
                                'service':service,
                                'usage_type':usage_type,
                                'cost':float(group['Metrics']['UnblendedCost']['Amount']),
                                'usage_quantity':float(group['Metrics']['UsageQuantity']['Amount']),
                                'normalized_usage':float(group['Metrics']['NormalizedUsageAmount']['Amount'])
                            }
                        )
                print(f"Collected {len(costs)} cost data from {start_date} to {end_date}")
                return pd.DataFrame(costs)
            except Exception as e:
                print(f"Error collecting cost data: {e}")
                return pd.DataFrame()
            
        def get_all_resources():
            resources=[]
            try:
                ec2_response=self.ec2.describe_instances()
                for reservation in ec2_response['Reservations']:
                    for instance in reservation['Instances']:
                        resources.append({
                    'InstanceId':instance['InstanceId'],
                    'InstanceType':instance['InstanceType'],
                    'State':instance['State']['Name'],
                    'LaunchTime':instance['LaunchTime'].strftime('%Y-%m-%d %H:%M'),
                    'Region':instance['Placement']['AvailabilityZone'],
                    'ResourceName':instance['Tags'][0]['Value'] if 'Tags' in instance and instance['Tags'] else 'N/A',
                    'Tags': {tag['Key']: tag['Value'] for tag in instance.get('Tags', [])}
                })
                ebs_response=self.ec2.describe_volumes()
                for volume in ebs_response['Volumes']:
                    resources.append({
                        'VolumeId':volume['VolumeId'],
                        'resource_id': volume['VolumeId'],
                        'size': volume['Size'],
                        'volume_type': volume['VolumeType'],
                        'state': volume['State'],
                        'attached': len(volume.get('Attachments', [])) > 0,
                        'tags': {tag['Key']: tag['Value'] for tag in volume.get('Tags', [])}
                    })
                buckets=self.s3.list_buckets()
                for bucket in buckets['Buckets']:
                    try:
                        Cw_response=self.cloudwatch.get_metric_statistics(
                            Namespace='AWS/S3',
                            MetricName='BucketSizeBytes',
                            StartTime=datetime.now() - timedelta(days=1),
                            EndTime=datetime.now(),
                            Dimensions=[
                                    {'Name': 'BucketName', 'Value': bucket['Name']},
                                    {'Name': 'StorageType', 'Value': 'StandardStorage'}
                                ],
                            Period=86400,
                            Statistics=['Average']
                        )
                        size=Cw_response['Datapoints'][0]['Average'] if Cw_response['Datapoints'] else 0
                        resources.append({
                            'resource_id': bucket['Name'],
                            'resource_type': 'S3',
                            'size_bytes': size,
                            'creation_date': bucket['CreationDate'].strftime('%Y-%m-%d %H:%M'),
                        })
                    except Exception as e:
                        pass
                print(f"Collected {len(resources)} resources")
                return pd.DataFrame(resources)
            except Exception as e:
                print(f"Error collecting resources: {e}")
                return pd.DataFrame()
        def get_resource_utilization(self):
            utilization=[]
            try:
                instances=self.ec2.describe_instances(
                    Filters=[{'Name':'instance-state-name','Values':['running']}]
                ) 
                for reservation in instances['Reservations']:
                    for instance in reservation['Instances']:
                        instance_id=instance['InstanceId']
                        cpu_metrics=self.cloudwatch.get_metric_statistics(
                            Namespace='AWS/EC2',
                            MetricName='CPUUtilization',
                            Dimensions=[{'Name':'InstanceId','Value':instance_id}],
                            StartTime=datetime.now() - timedelta(days=7),
                            EndTime=datetime.now(),
                            Period=3600,
                            Statistics=['Average','Maximum','Minimum']  
                        )  
                        for data_point in cpu_metrics['Datapoints']:
                            utilization.append({
                                'ResourceId':instance_id,
                                'metric':'CPUUtilization',
                                'timestamp':data_point['Timestamp'],
                                'average':data_point['Average'],
                                'maximum':data_point['Maximum'],
                                'minimum':data_point['Minimum'],
                            })
                print(f"Collected utilization data for {len(utilization)} entries")
                return pd.DataFrame(utilization)
            except Exception as e:
                print(f"Error collecting utilization data: {e}")
                return pd.DataFrame()