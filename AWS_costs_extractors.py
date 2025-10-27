import pandas as pd
import boto3
from datetime import datetime, timedelta

def get_acc_info():


    print("="*60)
    print("AWS ACCOUNT INFORMATION")
    print("="*60)
    
    sts = boto3.client('sts')
    
    try:
        identity = sts.get_caller_identity()
        print(f"Account ID: {identity['Account']}")
        print(f"User ARN: {identity['Arn']}")
        print(f"User ID: {identity['UserId']}")
    except Exception as e:
        print(f"Error: {str(e)}")

print(get_acc_info())

def get_ec2_instances():
    ec2 = boto3.client('ec2', region_name='us-east-1')
    try:
        response = ec2.describe_instances()
        instances=[]
        for reservation in response['Reservations']:
            for instance in reservation['Instances']:
                instances.append({
                    'InstanceId':instance['InstanceId'],
                    'InstanceType':instance['InstanceType'],
                    'State':instance['State']['Name'],
                    'LaunchTime':instance['LaunchTime'].strftime('%Y-%m-%d %H:%M'),
                    
                })
        if instances:
            df=pd.DataFrame(instances)
            print(df.to_string(index=False))
            print(f"\nTotal instances: {len(instances)}")
        else:
            print("No EC2 instances found - account is clean!")
        return instances
    except Exception as e:
        print(f"Error: {str(e)}")
        return []
get_ec2_instances()

def get_cost_and_usage(days_back=30):
    """
    Extract AWS cost and usage data for the last N days
    """
    print("Connecting to AWS Cost Explorer...")
    ce = boto3.client('ce', region_name='us-east-1')
    
    # Calculate date range
    end_date = datetime.now().date()
    start_date = end_date - timedelta(days=days_back)
    
    print(f"Fetching cost and usage from {start_date} to {end_date}")
    
    try:
        # Get cost and usage data
        response = ce.get_cost_and_usage(
            TimePeriod={
                'Start': str(start_date),  # ✅ Capital S
                'End': str(end_date)        # ✅ Capital E
            },
            Granularity='DAILY',
            Metrics=['UnblendedCost', 'UsageQuantity'],
            GroupBy=[
                {'Type': 'DIMENSION', 'Key': 'SERVICE'}
            ]
        )
        
        # Parse the response into a readable format
        results = []
        for day_data in response['ResultsByTime']:
            date = day_data['TimePeriod']['Start']
            
            for group in day_data['Groups']:
                service = group['Keys'][0]
                cost = float(group['Metrics']['UnblendedCost']['Amount'])
                usage = float(group['Metrics']['UsageQuantity']['Amount'])
                
                results.append({
                    'Date': date,
                    'Service': service,
                    'Cost': round(cost, 2),
                    'Usage': round(usage, 2)
                })
        
        # Convert to DataFrame
        df = pd.DataFrame(results)
        
        # Print summary
        print("\n" + "="*60)
        print("COST SUMMARY")
        print("="*60)
        
        if df.empty:
            print("No costs found - Your account is still in free tier!")
            print("This is good! Let's deploy some test resources to generate data.")
        else:
            total_cost = df['Cost'].sum()
            print(f"Total Cost (last {days_back} days): ${total_cost:.2f}")
            print("\nTop 5 Most Expensive Services:")
            print(df.groupby('Service')['Cost'].sum().sort_values(ascending=False).head())
        
        # Save to CSV
        output_file = f'aws_costs_{start_date}_to_{end_date}.csv'
        df.to_csv(output_file, index=False)
        print(f"\n✓ Data saved to: {output_file}")
        
        return df
        
    except Exception as e:
        print(f"Error: {str(e)}")
        print("\nCommon issues:")
        print("1. Make sure you've configured AWS CLI (run: aws configure)")
        print("2. Your IAM user needs 'ce:GetCostAndUsage' permission")
        print("3. Cost Explorer might take 24 hours to activate for new accounts")
        return None     
    
get_cost_and_usage()           


            